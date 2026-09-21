# AI 识别模拟器

该模块模拟独立的视觉识别服务，消费 `meter.recognition.queue`，然后使用
HMAC-SHA256 签名调用 Java 后端的开始、成功或失败回调接口。

## 启动

后端和模拟器必须使用相同的 `AI_SERVICE_SECRET_BASE64`。先启动 RabbitMQ 和
`meter-api`，再在 PowerShell 中执行：

```powershell
$env:AI_SERVICE_SECRET_BASE64 = "与 meter-api 相同的 Base64 密钥"
mvn -pl meter-ai-simulator spring-boot:run
```

默认使用 `SUCCESS` 模式，固定返回读数 `123.456` 和置信度 `0.9876`。

- `AI_SIMULATOR_MODE=FAILURE`：回调业务识别失败并正常 ACK。
- `AI_SIMULATOR_MODE=TRANSIENT_FAILURE`：触发 RabbitMQ 延迟重试，达到 3 次后进入 DLQ。
- `AI_SIMULATOR_RECOGNIZED_VALUE`：修改模拟读数。
- `AI_SIMULATOR_CONFIDENCE`：修改模拟置信度。

真实视觉识别服务接入时，实现 `RecognitionEngine`，根据消息中的
`bucketName` 和 `objectKey` 下载 OSS 图片并返回识别结果即可。
