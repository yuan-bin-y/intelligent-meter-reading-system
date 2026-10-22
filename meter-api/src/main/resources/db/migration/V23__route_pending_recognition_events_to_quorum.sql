-- 部署时先停止旧API/Worker。只迁移未完成事件的目标地址，不删除任何历史消息。
UPDATE mq_outbox_event
SET exchange_name = 'meter.recognition.v2.exchange', version = version + 1
WHERE exchange_name = 'meter.recognition.exchange'
  AND status IN ('PENDING', 'SENDING', 'FAILED');
