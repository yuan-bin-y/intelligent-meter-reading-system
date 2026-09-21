# 实时沟通 WebSocket 测试

REST 接口已经写入 `auth-api.openapi.yaml`。WebSocket 使用 STOMP 1.2，OpenAPI 不能直接表达完整的 STOMP 交互，因此在 Apifox 中按下面的信息新建 WebSocket 请求。

## 连接

- 地址：`ws://localhost:8070/ws/chat`
- 子协议：`v12.stomp`
- STOMP CONNECT 请求头：`Authorization: Bearer <Access Token>`
- JWT 必须是登录接口返回的 Access Token，不能使用 Refresh Token。
- CONNECT 成功后，服务端会把 JWT 中的 `userId` 作为当前 WebSocket 用户身份。

## 订阅

连接成功后订阅以下三个用户目的地：

| 目的地 | 返回数据 | 用途 |
| --- | --- | --- |
| `/user/queue/chat` | `ChatMessageVO` | 接收新消息和发送确认 |
| `/user/queue/chat.read` | `ChatReadReceiptVO` | 接收成员已读位置变化 |
| `/user/queue/chat.errors` | `ChatWebSocketErrorVO` | 接收参数、权限和状态错误 |

## 发送文本消息

发送目的地：`/app/chat.send`

```json
{
  "conversationId": 1,
  "clientMessageId": "web_20260921_001",
  "content": "您好，请帮我确认本次抄表读数。"
}
```

`clientMessageId` 由客户端生成。同一条消息因网络问题重试时必须复用原值，后端会返回已经保存的消息，只向发送者补发确认，不会让其他成员重复收到。

## 更新已读位置

发送目的地：`/app/chat.read`

```json
{
  "conversationId": 1,
  "lastReadMessageId": 105
}
```

服务端只允许已读位置向前推进，并将新的已读位置推送给会话中的全部有效成员。

## STOMP 原始帧示例

CONNECT：

```text
CONNECT
accept-version:1.2
host:localhost
Authorization:Bearer <Access Token>
heart-beat:0,0

```

发送消息：

```text
SEND
destination:/app/chat.send
content-type:application/json

{"conversationId":1,"clientMessageId":"web_20260921_001","content":"测试消息"}
```

每个 STOMP 帧末尾需要一个空字节 `NUL`。如果使用支持 STOMP 的客户端，客户端会自动补上。

## 前置条件

- 任务会话：先调用 `POST /api/v1/chat/conversations/task` 创建。
- 管理员客服会话：居民先调用 `POST /api/v1/chat/conversations/admin-support`，管理员再调用认领接口。
- 只有会话中的有效成员才能收发消息。
- 会话状态必须为 `ACTIVE`；`WAITING` 或 `CLOSED` 会话不能发送文本消息。
