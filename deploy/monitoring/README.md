# 本地基础设施与监控

## 启动

启动后会同时运行 RabbitMQ、Prometheus 和 Grafana。再启动后端，确认下面的地址可以访问：

```text
http://localhost:8070/actuator/prometheus
```

安装并启动 Docker Desktop 后，在项目根目录执行：

```powershell
docker compose -f deploy/monitoring/docker-compose.monitoring.yml up -d
```

## 访问地址

- RabbitMQ AMQP：`localhost:5672`
- RabbitMQ 管理后台：<http://localhost:15672>
- RabbitMQ 默认账号：`meter_app`
- RabbitMQ 默认密码：`meter-reading-rabbit`
- RabbitMQ Prometheus 指标：<http://localhost:15692/metrics>
- Prometheus：<http://localhost:9090>
- Prometheus Targets：<http://localhost:9090/targets>
- Grafana：<http://localhost:3000>
- Grafana 默认账号：`admin`
- Grafana 默认密码：`meter-reading-admin`

Grafana 首次启动会自动创建 Prometheus 数据源，并在“智能抄表系统”目录中加载
“智能抄表系统 - 后端监控”面板，不需要手动导入 JSON。

如需修改 RabbitMQ 账号、端口、数据保留时间或 Grafana 管理员密码，
将 `.env.example` 复制为 `.env` 后修改，并把相同的 RabbitMQ 账号配置给后端。

## 停止

```powershell
docker compose -f deploy/monitoring/docker-compose.monitoring.yml down
```

保留监控历史数据。如果需要同时删除 Prometheus 和 Grafana 数据卷，执行：

```powershell
docker compose -f deploy/monitoring/docker-compose.monitoring.yml down -v
```
