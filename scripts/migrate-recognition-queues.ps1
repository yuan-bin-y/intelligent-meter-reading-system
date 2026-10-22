param(
    [Parameter(Mandatory = $true)][string]$ManagementUrl,
    [string]$VirtualHost = '/'
)
$ErrorActionPreference = 'Stop'
if (!$env:RABBITMQ_USERNAME -or !$env:RABBITMQ_PASSWORD -or !$env:RABBITMQ_MIGRATION_URI) {
    throw '请通过环境变量设置RABBITMQ_USERNAME、RABBITMQ_PASSWORD、RABBITMQ_MIGRATION_URI，不要写进脚本。'
}
$encodedCredentials = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes(
    $env:RABBITMQ_USERNAME + ':' + $env:RABBITMQ_PASSWORD))
$headers = @{ Authorization = 'Basic ' + $encodedCredentials }
$vhostPath = [Uri]::EscapeDataString($VirtualHost)
$pairs = @(
    @('meter.recognition.queue', 'meter.recognition.v2.queue'),
    @('meter.recognition.retry.queue', 'meter.recognition.v2.queue'),
    @('meter.recognition.dlq', 'meter.recognition.v2.dlq')
)
foreach ($pair in $pairs) {
    $source = [Uri]::EscapeDataString($pair[0])
    $target = [Uri]::EscapeDataString($pair[1])
    # 两端队列必须已存在。脚本不创建错误类型的队列，也不删除原队列。
    $sourceInfo = Invoke-RestMethod -Uri "$ManagementUrl/api/queues/$vhostPath/$source" -Headers $headers
    $null = Invoke-RestMethod -Uri "$ManagementUrl/api/queues/$vhostPath/$target" -Headers $headers
    if ($sourceInfo.messages_unacknowledged -gt 0) { throw '旧消费者仍持有消息，请先停止旧API/Worker。' }
    if ($sourceInfo.messages_ready -eq 0) { continue }
    $body = @{ value = @{
        'src-protocol' = 'amqp091'; 'dest-protocol' = 'amqp091'
        'src-uri' = $env:RABBITMQ_MIGRATION_URI; 'dest-uri' = $env:RABBITMQ_MIGRATION_URI
        'src-queue' = $pair[0]; 'dest-queue' = $pair[1]
        'src-predeclared' = $true; 'dest-predeclared' = $true
        'ack-mode' = 'on-confirm'; 'src-delete-after' = 'queue-length'
        'reconnect-delay' = 5; 'src-prefetch-count' = 10
    }} | ConvertTo-Json -Depth 5
    $name = [Uri]::EscapeDataString('meter-upgrade-' + $pair[0])
    $null = Invoke-RestMethod -Method Put -Uri "$ManagementUrl/api/parameters/shovel/$vhostPath/$name" `
        -Headers $headers -ContentType 'application/json' -Body $body
    Write-Output ('已安排确认后迁移：' + $pair[0] + ' -> ' + $pair[1])
}
Write-Output '请在管理页面确认旧队列Ready/Unacked均为0，并检查Shovel状态。脚本不会删除队列。'
