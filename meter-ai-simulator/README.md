# AI 任务 Worker

该 Maven 模块是 Java 侧 AI Worker。它消费 RabbitMQ 的
`meter.recognition.queue`，根据识别任务中的 OSS Bucket 和 ObjectKey 下载图片，
调用仓库内的 [`meter-ai-service`](../meter-ai-service/) Python 服务，再使用
HMAC-SHA256 签名回调 `meter-api`。

目录名保留为 `meter-ai-simulator`，因为它也提供成功、失败和瞬时失败模拟模式，
便于在不启动模型时验证 MQ 重试和死信队列。Spring 应用名已经改为
`meter-ai-worker`。

## 真实模型模式

先启动 RabbitMQ、`meter-api` 和 Python 模型服务，再执行：

```powershell
$env:AI_SERVICE_SECRET_BASE64 = "与 meter-api 相同的 Base64 密钥"
$env:AI_SIMULATOR_MODE = "MODEL"
$env:AI_MODEL_BASE_URL = "http://127.0.0.1:8001"
$env:OSS_ENDPOINT = "你的 OSS Endpoint"
$env:OSS_ACCESS_KEY_ID = "你的 AccessKey ID"
$env:OSS_ACCESS_KEY_SECRET = "你的 AccessKey Secret"
mvn -pl meter-ai-simulator spring-boot:run
```

## 测试模式

- `AI_SIMULATOR_MODE=SUCCESS`：返回配置的固定读数。
- `AI_SIMULATOR_MODE=FAILURE`：回调业务识别失败并正常 ACK。
- `AI_SIMULATOR_MODE=TRANSIENT_FAILURE`：触发 RabbitMQ 延迟重试，达到 3 次后进入 DLQ。
- `AI_SIMULATOR_RECOGNIZED_VALUE`：测试读数。
- `AI_SIMULATOR_CONFIDENCE`：测试置信度。
