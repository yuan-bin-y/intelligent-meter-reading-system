# Python 视觉识别服务

该目录是智能抄表系统的一部分，负责图片质量检测、表盘区域检测和读数识别。
它只提供算法 HTTP API，不直接访问 MySQL、Redis、RabbitMQ 或 OSS。Java 侧
`meter-ai-simulator` 模块作为 AI Worker 负责 MQ 消费、OSS 下载和回调后端。

## 本地启动

需要 Python 3.12。首次运行安装依赖：

```powershell
cd meter-ai-service
py -3.12 -m pip install -r requirements.txt
Copy-Item .env.example .env
py -3.12 -m uvicorn app.main:app --host 0.0.0.0 --port 8001
```

健康检查和接口文档：

- `GET http://127.0.0.1:8001/health`
- `http://127.0.0.1:8001/docs`

识别入口是 `POST /api/v1/recognize`，请求为 `multipart/form-data`，图片字段为
`image`，可选字段为 `meter_type`、`dial_type` 和 `previous_reading`。

## 测试

```powershell
cd meter-ai-service
py -3.12 -m pytest -q
```

`models/` 保存推理所需的 YOLO 和 PaddleOCR 模型，`samples/` 保存本地验证图片；
二者的二进制文件均被 Git 忽略。新机器请先按 [`models/README.md`](models/README.md)
准备模型文件。
