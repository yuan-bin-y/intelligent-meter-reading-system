# 可靠性修复验收记录

日期：2026-10-22。环境：Windows、Docker Desktop、JDK 17.0.20。

## 修复内容

| 问题 | 处理方案 | 验证方式 |
| --- | --- | --- |
| 密钥轮换后Redis失败导致接口结果与数据库不一致 | MySQL事务保存新凭证、幂等请求结果和清理Outbox；在线状态核对凭证版本，清理只作用于旧版本 | 真实MySQL事务、注入Redis清理失败、后台重试、重复请求、旧心跳竞争；实际HTTP新增/轮换/查询 |
| 过期锁持有者覆盖新缓存 | 看门狗续期、单调令牌、Lua原子校验令牌与失效代次；锁桶固定上限，元数据过期 | 在真实Redis中让旧锁租约过期，新持有者写入后再尝试旧写入；失效期间旧加载拒写 |
| Redis故障导致无限回源，删除失败只等TTL | 同Key合并、进程内回源并发上限、无排队、请求与SQL超时、503受控拒绝；失效任务同事务持久化 | 256并发请求及真实慢SQL故障注入；超时不释放仍在执行的槽位 |
| classic队列内部死信转发边界 | v2 Quorum队列，至少一次死信转发与reject-publish；保留Confirm/Return后ACK；提供确认后迁移脚本 | 路由缺失恢复、Broker重启、未ACK重新投递、Outbox已发送但数据库确认失败、Shovel实际迁移 |
| 仓库没有核心测试、CI跳过测试 | 核心Java测试提交Git，CI启动独立MySQL/Redis/RabbitMQ并执行verify | 本机完整执行35项测试，0失败、0错误、0跳过；所有模块构建、Checkstyle、SpotBugs通过 |
| 缺少容量数据和扩容依据 | 独立验收环境、完整消息链路负载脚本、可配置Worker并发、回源/清理/Outbox指标及告警 | 实际运行API与Worker，3轮各60任务；Prometheus官方promtool校验配置和5条规则 |

数据库新增Flyway V22（轮换请求结果、Redis清理Outbox）与V23（旧未完成Outbox路由迁移），已在隔离库实际执行至V23。生产数据未迁移，旧Broker队列未被删除。

## 自动化回归

执行：

```powershell
docker compose -f deploy/testing/compose.yml up -d --wait
$env:METER_IT='true'
$env:METER_IT_BROKER_RESTART='true'
mvn --batch-mode --no-transfer-progress verify
```

35项测试全部通过。包含真实MySQL和Redis操作、Broker重启测试。日志中的注入异常与“已确认但状态更新失败”是故障测试的预期输出，应以Surefire结果判断是否通过。

Redis故障突发测试设置回源并发上限为4，256个请求中4个完成数据库加载，252个受到容量保护，实际数据库加载峰值为4。测试使用 `SLEEP(0.05)` 注入慢SQL，只验证保护机制，不把这个数值当成真实业务吞吐。

Shovel迁移另外在测试Broker实际执行：classic旧主队列的一条专用探针迁移到Quorum新主队列，内容保持一致。目标确认后才清理源消息。验证后已消费探针，未删除源队列和数据卷。

## 完整消息链路短测

链路：HTTP创建识别任务 → MySQL业务事务与Outbox → RabbitMQ Quorum → 真实Java Worker → HMAC签名HTTP回调 → MySQL抄表结果与待审核任务。

每轮60任务，HTTP并发8、prefetch1，识别器使用50毫秒可控测试响应。各轮全部成功，HTTP失败数均为0；分别生成60条抄表结果和60个待审核任务。

| Worker并发 | 总耗时 | 完成任务/秒 | 提交接口P95 | 完成延迟P95 | 采样到的活跃数据库连接峰值 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 1 | 33.453秒 | 1.794 | 969毫秒 | 28.016秒 | 3 |
| 2 | 10.734秒 | 5.590 | 234毫秒 | 9.094秒 | 2 |
| 4 | 11.703秒 | 5.127 | 281毫秒 | 10.579秒 | 12 |

原始采样：[并发1](recognition-load-1.json)、[并发2](recognition-load-2.json)、[并发4](recognition-load-4.json)。并发2这轮还执行了最终构建中的设备HTTP新增、详情缓存、轮换和相同请求ID重试，全部通过，重复请求没有再次增加版本。

这是共享本机资源的短测，没有隔离CPU/磁盘，也没有做长时间稳态压测；各轮缓存、测试库大小与JIT预热情况有差别。完成延迟包含轮询采样误差，RabbitMQ管理指标可能滞后，观测到Ready为0不代表没有积压。识别图片为测试元数据，结果不包含真实OSS上传与模型推理成本，不能据此宣称生产QPS或真实模型吞吐。

并发4没有比并发2更快，且曾触及连接池12个活跃连接；继续增加消费者可能把瓶颈移到数据库或模型服务。默认并发仍保留1，可结合目标环境逐步调整并测量。单机Compose仍不是跨主机高可用部署。

## 监控与复现材料

- `scripts/run-recognition-load.ps1`：启动隔离API/Worker，结束后只关闭自己创建的Java进程。
- `scripts/measure-recognition.py`：真实HTTP与数据库结果校验，JSON报告不包含Token、密码和设备密钥。
- `deploy/testing/compose.yml`：独立测试库、Redis与RabbitMQ，不依赖本机OSS、前端、模型或私密配置。
- `.github/workflows/ci.yml`：已配置同类隔离服务与故障回归；没有代用户推送或声称远端Actions已运行。
- `deploy/monitoring/prometheus/reliability.rules.yml`：5条规则已经promtool检查通过；完整Prometheus配置与Compose配置也校验通过。
- Grafana后端面板增加清理/Outbox积压、回源拒绝/超时两组曲线；本轮检查了JSON配置，没有启动用户的整套监控部署。

部署升级、请求重试语义、参数与监控说明见[可靠性方案](../hardening.md)。
