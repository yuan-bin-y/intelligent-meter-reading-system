# 多场景智能抄表系统

Java 负责业务、认证、设备、任务、图片、审核、消息和监控，Python 负责表盘图片
识别，Vue 负责管理端页面。

## 工程结构

| 目录 | 作用 |
| --- | --- |
| `meter-api` | Spring Boot 启动模块和 REST/WebSocket 接口，默认端口 8070 |
| `meter-auth` | Spring Security、JWT、BCrypt、Redis 会话与限流 |
| `meter-service` | 用户、表具、设备、任务、图片、AI、审核、聊天等业务实现 |
| `meter-model` | Entity、DTO、VO、枚举 |
| `meter-common` | 统一响应、异常、TraceId 等公共能力 |
| `meter-web` | Vue 3 管理端，开发端口 5173 |
| `meter-ai-simulator` | Java AI Worker；消费 MQ、下载 OSS 图片、调用 Python 并回调后端 |
| `meter-ai-service` | Python FastAPI 视觉识别服务及模型，默认端口 8001 |
| `deploy` | Prometheus、Grafana 等部署配置 |

## AI 识别链路

```text
设备上传图片
  -> Java 写入识别任务与 Outbox
  -> Outbox 发布 RabbitMQ 消息
  -> meter-ai-simulator 消费消息并从 OSS 下载图片
  -> meter-ai-service 识别图片
  -> Worker 签名回调 meter-api
  -> Java 保存识别结果并推进任务状态
```

Python 服务的安装和启动参见
[`meter-ai-service/README.md`](meter-ai-service/README.md)，Java Worker 的运行参数参见
[`meter-ai-simulator/README.md`](meter-ai-simulator/README.md)。所有密钥通过环境变量注入，
不要写入源码或提交 `.env`、`application-local.yml`。

## 前端启动

先启动端口为 8070 的 `meter-api`，再执行：

```powershell
cd meter-web
npm install
npm run dev
```

浏览器访问 `http://127.0.0.1:5173`。开发服务器会把 `/api` 和 `/ws` 请求代理到 Java 后端。

当前页面覆盖管理员工作台、用户/表具/设备/任务管理、告警、AI 识别、抄表图片、
结果审核、正式记录、审计日志，以及抄表员任务、居民表具、通知、账户和实时聊天。

完整 Docker 部署配置见 [`deploy/README.md`](deploy/README.md)。该部署默认使用独立端口和新数据卷，不会自动迁移当前本机数据库。
