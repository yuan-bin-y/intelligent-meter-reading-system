# 本地监控

## 启动

先启动后端，确认下面的地址可以访问：

```text
http://localhost:8070/actuator/prometheus
```

安装并启动 Docker Desktop 后，在项目根目录执行：

```powershell
docker compose -f deploy/monitoring/docker-compose.monitoring.yml up -d
```

## 访问地址

- Prometheus：<http://localhost:9090>
- Prometheus Targets：<http://localhost:9090/targets>
- Grafana：<http://localhost:3000>
- Grafana 默认账号：`admin`
- Grafana 默认密码：`meter-reading-admin`

Grafana 首次启动会自动创建 Prometheus 数据源，并在“智能抄表系统”目录中加载
“智能抄表系统 - 后端监控”面板，不需要手动导入 JSON。

如需修改端口、数据保留时间或管理员密码，将 `.env.example` 复制为 `.env` 后修改。

## 停止

```powershell
docker compose -f deploy/monitoring/docker-compose.monitoring.yml down
```

保留监控历史数据。如果需要同时删除 Prometheus 和 Grafana 数据卷，执行：

```powershell
docker compose -f deploy/monitoring/docker-compose.monitoring.yml down -v
```
