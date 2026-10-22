# 业务可靠性修复与验证

## 设备凭证轮换

`POST /api/v1/admin/devices/{deviceId}/secret/reset` 现在要求：

```json
{"version": 0, "requestId": "bd27d965-d4d8-42af-8f58-6ace56849258"}
```

客户端为一次新操作生成 UUID；超时重试复用原请求的 UUID 和 version。同一设备的轮换使用 MySQL 行锁串行处理，设备更新、轮换结果、Redis 清理任务在同一事务内提交。重试命中已提交结果时 `replayed=true`，不会再次换密钥。请求ID不能复用于不同操作人或不同版本。

明文密钥不持久化，只在第一次成功响应返回。若首次响应丢失，重试可确认轮换是否已提交，但不能恢复密钥；管理员须查询最新设备版本，使用新请求ID再次轮换并保存返回值。`replayed=true` 返回的是该次轮换结果，设备可能已被后续操作修改，不能把历史 version 当作当前版本。

在线状态读取同时核对 MySQL 凭证版本。心跳 Lua 禁止旧版本覆盖新版本；后台清理只删除低于目标版本的心跳。停用时递增凭证版本，防止停用前的在途请求在重新启用后恢复在线状态；密钥摘要不变，设备重新认证后获得新版本。

`redis_maintenance_task` 是可重试的清理 Outbox。任务租约60秒，多实例条件领取，完成时核对领取令牌；失败按5秒至5分钟退避，持续重试，未完成任务不自动删除。完成记录保留7天。日志包含 taskId 和原业务 traceId，不记录密钥。部署后应监测最老未完成任务，避免把持续故障误认为已经修复。

## 缓存写入和失效

详情缓存改用v2 Key，旧Key自行过期。Redisson `RFencedLock` 不再指定固定租约，采用看门狗续期；获得单调递增令牌后登记本次加载。Lua写入同时核对令牌和失效代次，旧持有者不能覆盖新持有者的结果。业务更新后，失效操作原子更换代次并删除值，更新前启动的查询不能重新填入旧值。元数据丢失时拒绝旧加载结果。

表具和设备的更新与缓存失效任务在同一个MySQL事务中提交；提交后立即尝试失效，失败由 `redis_maintenance_task` 重试。默认缓存TTL为60秒，加0至15秒抖动；配置可以按允许的陈旧时间调整。此处仍是最终一致缓存，故障期间可能读到TTL以内的旧详情。设备认证与凭证版本读取直接查询MySQL，不用详情缓存做安全判断。

## Redis故障时限制数据库回源

详情和角色缓存未命中、Redis故障、取锁失败、关闭缓存，统一经过 `DatabaseLoadGate`。同一JVM内同一Key共享一次加载；不同Key最多8次同时加载，无等待队列。满额直接返回HTTP503，业务码 `UPSTREAM_ERROR`；默认5秒请求超时。底层SQL若尚未结束，仍占用原槽位，不能因请求已超时就放行更多SQL；超时任务不会再填充缓存。

回源运行在线程池内，保留traceId，避免继承请求事务里的旧快照。写事务仅加在修改方法，缓存命中不会提前占用数据库连接。默认MyBatis语句超时3秒，连接池等待2秒，驱动socket超时6秒。慢报表需要单独设置更合适的语句超时，不能通过无限放大缓存回源并发来解决。

并发额度是每个Java进程的上限；N个副本最多N×8个加载，应结合MySQL容量、连接池12及写请求余量调整 `BUSINESS_CACHE_LOAD_CONCURRENCY`。这项保护覆盖业务缓存回源，不能替代所有分页接口的限流或数据库容量规划。

Prometheus新增 `business_cache_load_active`、`business_cache_load_inflight`、`business_cache_load_rejected_total`、`business_cache_load_timeout_total` 和 `business_cache_load_coalesced_total`。Redis读失败后短暂跳过重复探测，减少故障期间日志与连接风暴。

## RabbitMQ死信保障与升级

API与Worker同时改为v2交换机/队列，主队列、重试队列、最终DLQ均为Quorum。主队列与重试队列设置 `x-dead-letter-strategy=at-least-once`、`x-overflow=reject-publish`，使内部死信转发等待目标队列确认。队列最多10万条，满额拒绝新发布；Outbox或原队列保留待重试消息。最终DLQ仍由Worker显式发布，核对Confirm与Return后才ACK原消息。重复投递不可避免，eventId和attemptNo仍需参与业务幂等。

单节点Quorum不能提供跨节点高可用。生产保障需要至少三个RabbitMQ节点并确认每个队列拥有三个成员；多数副本永久丢失、磁盘损坏或人为删除仍可能丢数据，不能宣称“绝不丢失”。参考[Quorum队列官方说明](https://www.rabbitmq.com/docs/quorum-queues)。

已有classic队列不能原地改类型。升级顺序：

1. 停止旧API与Worker，保留RabbitMQ数据卷和旧队列。
2. 部署新API，让Flyway V23调整未完成Outbox的交换机地址，并声明v2拓扑。暂不启动新Worker。
3. 启用 `rabbitmq_shovel` 和 `rabbitmq_shovel_management` 插件（Compose插件文件已包含）。通过环境变量设置管理员账号以及Broker内部可访问的 `RABBITMQ_MIGRATION_URI`，格式为 `amqp://用户:URL编码的密码@rabbitmq:5672/%2f`。
4. 执行 `./scripts/migrate-recognition-queues.ps1 -ManagementUrl http://localhost:15673`。脚本安排Shovel以 `on-confirm` 迁移旧主队列、旧重试队列到新主队列，旧DLQ到新DLQ。旧重试消息迁移后可提前执行一次，但保留eventId、attemptNo和x-death计数。
5. 在管理页面检查旧队列Ready/Unacked均为0、Shovel没有失败，再启动新Worker。脚本不删除旧队列、消息或数据卷。升级前已过期/失败的历史事件仍需按业务状态判断是否人工重试。

Shovel只在目标确认后ACK源消息；中途故障可能重复转发，由业务幂等处理。参考[官方Shovel确认语义](https://www.rabbitmq.com/docs/4.3/shovel-dynamic)。迁移凭据是Broker运行参数中的敏感信息，应限制管理权限，完成后确认临时Shovel已自动结束。

## 运行隔离测试

测试容器与日常业务容器分开，不读取本地配置、OSS、JWT密钥或模型文件：

```powershell
docker compose -f deploy/testing/compose.yml up -d --wait
$env:METER_IT='true'
mvn --batch-mode --no-transfer-progress verify
```

MySQL 使用 `13306/meter_hardening`，Redis 使用16389，RabbitMQ 使用15680（管理页面15681）。账号仅供本机隔离测试，不能用于真实部署。默认不设置 `METER_IT` 时仍执行所有不依赖外部服务的单元测试。

测试结束可运行 `docker compose -f deploy/testing/compose.yml down`；此配置不挂载业务数据卷。不要对日常部署执行删除数据卷操作。
