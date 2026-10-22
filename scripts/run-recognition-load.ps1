param(
    [ValidateRange(1, 16)][int]$WorkerConcurrency = 1,
    [ValidateRange(1, 1000)][int]$Tasks = 60,
    [string]$Python = 'python',
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
& $Python -m pip --version *> $null
if ($LASTEXITCODE -eq 0) {
    & $Python -m pip install -r scripts/requirements-validation.txt
} elseif (Get-Command uv -ErrorAction SilentlyContinue) {
    uv pip install --python $Python -r scripts/requirements-validation.txt
} else {
    throw '指定的Python没有pip，请先安装pip或使用带pip的虚拟环境。'
}
if ($LASTEXITCODE -ne 0) { throw '验收脚本依赖安装失败' }
if (Get-NetTCPConnection -LocalPort 18070 -State Listen -ErrorAction SilentlyContinue) {
    throw '18070已被占用。脚本不会终止已有进程，请先检查。'
}
docker compose -f deploy/testing/compose.yml up -d --wait
if ($LASTEXITCODE -ne 0) { throw '测试容器启动失败' }
if (!$SkipBuild) {
    $env:METER_IT = 'true'
    mvn --batch-mode --no-transfer-progress verify
    if ($LASTEXITCODE -ne 0) { throw '构建或回归测试失败' }
}
$overrides = @{
    SPRING_PROFILES_ACTIVE = 'acceptance'; SERVER_PORT = '18070'; SERVER_ADDRESS = '127.0.0.1'
    SPRING_DATASOURCE_URL = 'jdbc:mysql://127.0.0.1:13306/meter_hardening?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai'
    SPRING_DATASOURCE_USERNAME = 'root'; SPRING_DATASOURCE_PASSWORD = 'test-only-root'
    SPRING_DATA_REDIS_HOST = '127.0.0.1'; SPRING_DATA_REDIS_PORT = '16389'
    RABBITMQ_HOST = '127.0.0.1'; RABBITMQ_PORT = '15680'
    RABBITMQ_USERNAME = 'test'; RABBITMQ_PASSWORD = 'test-only-password'; OSS_ENABLED = 'false'
    JWT_SECRET_BASE64 = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(64))
    AI_SERVICE_SECRET_BASE64 = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(64))
    METER_BACKEND_BASE_URL = 'http://127.0.0.1:18070'; AI_SIMULATOR_MODE = 'SUCCESS'
    AI_SIMULATOR_PROCESSING_DELAY = 'PT0.05S'; AI_WORKER_CONCURRENCY = "$WorkerConcurrency"
    AI_WORKER_MAX_CONCURRENCY = "$WorkerConcurrency"; AI_WORKER_PREFETCH = '1'
    AUTH_REGISTER_PER_HOUR = '100'; AUTH_LOGIN_PER_MINUTE = '100'
    LOG_PATH = "$projectRoot/tmp/acceptance-logs"
}
$previous = @{}
$apiProcess = $null
$workerProcess = $null
New-Item -ItemType Directory -Force -Path tmp | Out-Null
try {
    foreach ($entry in $overrides.GetEnumerator()) {
        $previous[$entry.Key] = [Environment]::GetEnvironmentVariable($entry.Key, 'Process')
        [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
    }
    $apiJar = Join-Path $projectRoot 'meter-api/target/meter-api-0.0.1-SNAPSHOT.jar'
    $apiProcess = Start-Process java -ArgumentList @('-Xmx512m', '-jar', ('"' + $apiJar + '"')) `
        -WindowStyle Hidden -PassThru -RedirectStandardOutput tmp/acceptance-api.log -RedirectStandardError tmp/acceptance-api-error.log
    $ready = $false
    for ($attempt = 0; $attempt -lt 90; $attempt++) {
        if ($apiProcess.HasExited) { throw '隔离API启动失败，请查看tmp/acceptance-api.log' }
        try {
            $health = Invoke-RestMethod http://127.0.0.1:18070/actuator/health -TimeoutSec 2
            if ($health.status -eq 'UP') { $ready = $true; break }
        } catch { }
        Start-Sleep -Seconds 1
    }
    if (!$ready) { throw '隔离API未能在限定时间内就绪' }
    $workerJar = Join-Path $projectRoot 'meter-ai-simulator/target/meter-ai-simulator-0.0.1-SNAPSHOT.jar'
    $workerProcess = Start-Process java -ArgumentList @('-Xmx512m', '-jar', ('"' + $workerJar + '"')) `
        -WindowStyle Hidden -PassThru -RedirectStandardOutput tmp/acceptance-worker.log -RedirectStandardError tmp/acceptance-worker-error.log
    Start-Sleep -Seconds 8
    if ($workerProcess.HasExited) { throw '隔离Worker启动失败，请查看tmp/acceptance-worker.log' }
    $report = & $Python -B scripts/measure-recognition.py --tasks $Tasks --worker-concurrency $WorkerConcurrency
    $measurementExit = $LASTEXITCODE
    if ($report) {
        $parsed = $report | ConvertFrom-Json
        $parsed | ConvertTo-Json -Depth 20 | Set-Content -Encoding utf8 "tmp/recognition-load-$WorkerConcurrency.json"
        $parsed | Select-Object passed, tasks, succeeded, workerConcurrency, elapsedSeconds, throughputTasksPerSecond, httpP95Ms, completionP95Ms
    }
    if ($measurementExit -ne 0) { throw '链路压测未通过，请结合报告与traceId检查隔离服务日志' }
} finally {
    # 只关闭本脚本创建的进程，测试容器与结果保留供排查。
    foreach ($process in @($workerProcess, $apiProcess)) {
        if ($null -ne $process -and !$process.HasExited) { Stop-Process -Id $process.Id }
    }
    foreach ($key in $previous.Keys) { [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process') }
}
