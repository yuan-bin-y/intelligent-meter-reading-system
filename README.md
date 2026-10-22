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
| `meter-web` | 本机 Vue 3 管理端，开发端口 5173；整个目录不随 Git 提交 |
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

前端仅保留在本机。新拉取的仓库不包含 `meter-web`，需要单独取得该目录才能运行前端或构建包含前端的 Docker 部署。

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

## 开发检查与测试

Java 核心业务测试随仓库交付，不需要真实模型或 OSS。密码哈希生成工具、前端、Python 模型实验、本机配置和模型样本继续在本机维护。正式识别链路使用 YOLO + PaddleOCR；Java AI Worker 仍属于正式服务。

Java 使用 JDK 17，在根目录执行 `mvn verify`，运行业务测试、Checkstyle、SpotBugs 和构建，并生成 JaCoCo 覆盖率报告。规则放在 `config/`。

本机保留前端源码时，使用 Node.js 22.13 或更新的受支持版本，在 `meter-web` 执行：

```sh
npm ci
npm run check
```

`check` 包括 ESLint、Prettier、TypeScript 类型检查和生产构建。需要整理格式时执行 `npm run format`。`npm run test`、`npm run test:coverage` 仅供保留了本地测试文件的开发环境使用。

Python 使用独立的 Python 3.12 虚拟环境，在 `meter-ai-service` 执行：

```sh
python -m pip install -r requirements-ci.txt
ruff check app
ruff format --check app
```

本机保留 `tests/`、`pytest.ini` 和模拟识别器时，仍可执行 `python -m pytest`。真实 PaddleOCR 测试通过 `AI_INTEGRATION_IMAGE` 指定图片，再执行 `python -m pytest -m integration`。这些测试文件不包含在新拉取的正式源码中。

GitHub Actions 配置位于 `.github/workflows/ci.yml`，推送、PR 和手动触发时执行 Java 单元测试、隔离 MySQL/Redis/RabbitMQ 故障回归、构建与静态检查、Python 静态检查。CI 不依赖本地前端和实验识别器。密钥轮换、缓存一致性、数据库回源保护及队列升级步骤见[可靠性说明](docs/hardening.md)。

正式识别由 `recognizers/paddle_ocr.py` 编排，`pipeline/detect.py` 负责显示区定位，`recognizers/ocr_output.py` 适配 PaddleOCR 输出。OpenCV 数字模板、指针和 Mock 实验代码只保留在本机，按需加载。
