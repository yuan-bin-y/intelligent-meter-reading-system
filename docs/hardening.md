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

## 运行隔离测试

测试容器与日常业务容器分开，不读取本地配置、OSS、JWT密钥或模型文件：

```powershell
docker compose -f deploy/testing/compose.yml up -d --wait
$env:METER_IT='true'
mvn --batch-mode --no-transfer-progress verify
```

MySQL 使用 `13306/meter_hardening`，Redis 使用16389，RabbitMQ 使用15680（管理页面15681）。账号仅供本机隔离测试，不能用于真实部署。默认不设置 `METER_IT` 时仍执行所有不依赖外部服务的单元测试。

测试结束可运行 `docker compose -f deploy/testing/compose.yml down`；此配置不挂载业务数据卷。不要对日常部署执行删除数据卷操作。
