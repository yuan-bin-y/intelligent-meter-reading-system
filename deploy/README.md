# 完整 Docker 部署

`docker-compose.yml` 包含 MySQL、Redis、RabbitMQ、Java API、Java AI Worker、Python 识别服务、Vue 前端、Prometheus 和 Grafana。它使用独立的数据卷和宿主机端口，可以与目前的 Windows 本机服务并存；**新 MySQL 数据卷不会自动包含本机 MySQL 的业务数据**。

## 启动

仓库中的 `deploy/.env.example` 列出了全部变量。`deploy/.env` 已加入 `.gitignore`，不能提交。首次部署时复制示例，填入不同的强密码、两个不同的 Base64 随机密钥，以及 OSS 配置。AI Worker 和 API 必须使用同一个 `AI_SERVICE_SECRET_BASE64`。

视觉模型权重不在 Git 中。构建 Python 镜像前，先按照 [`meter-ai-service/models/README.md`](../meter-ai-service/models/README.md) 将模型文件放到本机对应路径；Docker 构建会检查文件是否存在。

在项目根目录运行：

```powershell
Copy-Item deploy/.env.example deploy/.env
docker compose --env-file deploy/.env -f deploy/docker-compose.yml config --quiet
docker compose --env-file deploy/.env -f deploy/docker-compose.yml build
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d
docker compose --env-file deploy/.env -f deploy/docker-compose.yml ps
```

如果 `deploy/.env` 已存在，不要再次执行 `Copy-Item` 覆盖其中的配置。此文件不在 Git 中。

首次启动时，MySQL 会创建空库，API 启动时 Flyway 自动建表和执行版本脚本。Python 服务首次启动还可能下载 PaddleOCR 的官方模型；下载缓存保存在 `paddle-cache` 卷中。Java AI Worker 会等 API、RabbitMQ 和 Python 服务健康后启动。

| 服务 | 宿主机地址（默认） |
| --- | --- |
| 前端 | http://127.0.0.1:8080 |
| Java API | http://127.0.0.1:8071/actuator/health |
| Python 识别 | http://127.0.0.1:8002/health |
| MySQL（DataGrip） | 127.0.0.1:3307，库 `meter_reading`，账号和密码见 `deploy/.env` |
| Redis | 127.0.0.1:6380 |
| RabbitMQ 管理页 | http://127.0.0.1:15673 |
| Prometheus | http://127.0.0.1:9091/targets |
| Grafana | http://127.0.0.1:3001 |

容器之间使用服务名通信：API 连 `mysql`、`redis`、`rabbitmq`；Worker 连 `rabbitmq`、`meter-api`、`meter-ai-service`。前端 Nginx 将 `/api` 和 `/ws` 转发到 API。Docker Prometheus 抓取容器内的 API 和 RabbitMQ，不依赖 Windows 宿主机地址。

## 现有数据与切换

这套 Compose 默认使用新数据卷，不会改动当前 Windows MySQL、Redis、RabbitMQ，也不会自动迁移账号、表具、图片元数据或消息。先验证新环境，再决定是否从本机 MySQL 导出并导入 Docker MySQL。迁移时应暂停原环境写入，保留数据库备份，并单独检查 Redis 会话和 RabbitMQ 未处理消息；不要对已有数据卷执行 `down -v`。

查看运行日志：

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.yml logs --tail 100 meter-api meter-ai-worker meter-ai-service
```

只停止这套容器（保留数据卷）：

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.yml down
```

