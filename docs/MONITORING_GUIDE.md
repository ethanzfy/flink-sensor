# Flink传感器应用监控指南

本文档介绍如何使用Spring Boot Actuator进行传感器应用的监控。

## 监控端点概述

传感器应用提供了多个自定义监控端点，可以通过HTTP API访问：

### 1. WebSocket连接监控 `/actuator/websocket-stats`

此端点提供WebSocket连接的实时统计信息，包括：
- 当前活跃连接数
- 会话详情（客户端IP、用户代理、连接时间和最后活动时间）

示例请求：
```
GET http://localhost:8080/actuator/websocket-stats
```

示例响应：
```json
{
  "activeConnections": 3,
  "sessions": {
    "session-id-1": {
      "remoteAddress": "192.168.1.10",
      "userAgent": "Mozilla/5.0...",
      "connectedAt": 1634567890123,
      "lastActivity": 1634567895678
    },
    "session-id-2": {
      "remoteAddress": "192.168.1.11",
      "userAgent": "Mozilla/5.0...",
      "connectedAt": 1634567891234,
      "lastActivity": 1634567896789
    }
  }
}
```

### 2. 最新数据缓存状态 `/actuator/data-cache`

此端点提供传感器数据缓存的状态信息，包括：
- 缓存项数量
- 最近更新时间
- 部分数据样本

示例请求：
```
GET http://localhost:8080/actuator/data-cache
```

示例响应：
```json
{
  "itemCount": 5,
  "lastUpdateTime": 1634567899999,
  "lastUpdateTimeFormatted": "Mon Oct 18 12:34:59 CST 2021",
  "ageSeconds": 5.623,
  "samples": {
    "device_1": {
      "deviceId": "device_1",
      "timestamp": 1634567899999,
      "value": 23.45
    },
    "device_2": {
      "deviceId": "device_2",
      "timestamp": 1634567899000,
      "value": 26.78
    }
  }
}
```

### 3. 消息队列积压预警 `/actuator/message-queue`

此端点提供消息队列的状态信息，包括：
- 当前队列大小
- 已处理消息总数
- 队列状态评估
- 使用率百分比

示例请求：
```
GET http://localhost:8080/actuator/message-queue
```

示例响应：
```json
{
  "currentSize": 15,
  "totalProcessed": 1250,
  "threshold": 100,
  "status": "NORMAL",
  "backpressureDetected": false,
  "utilizationPercentage": 15.0
}
```

队列状态可能为以下几种：
- `IDLE`：队列为空
- `NORMAL`：队列使用率 < 50%
- `BUSY`：队列使用率 50-80%
- `WARNING`：队列使用率 80-100%
- `CRITICAL`：队列使用率 > 100%（溢出）

### 4. 异常次数统计 `/actuator/exception-stats`

此端点提供应用中异常的统计信息，包括：
- 各类异常的计数
- 异常百分比分布
- 最近发生的异常详情

示例请求：
```
GET http://localhost:8080/actuator/exception-stats
```

示例响应：
```json
{
  "counts": {
    "IOException": 12,
    "IllegalArgumentException": 3,
    "SerializationException": 1
  },
  "totalCount": 16,
  "percentages": {
    "IOException": 75.0,
    "IllegalArgumentException": 18.75,
    "SerializationException": 6.25
  },
  "recentExceptions": [
    {
      "type": "IOException",
      "message": "连接拒绝",
      "timestamp": 1634567897123,
      "formattedTime": "Mon Oct 18 12:34:57 CST 2021"
    },
    {
      "type": "IllegalArgumentException",
      "message": "无效设备ID: null",
      "timestamp": 1634567896123,
      "formattedTime": "Mon Oct 18 12:34:56 CST 2021"
    }
  ]
}
```

### 5. Prometheus指标 `/actuator/prometheus`

此端点提供符合Prometheus格式的指标数据，可以直接与Prometheus监控系统集成。

示例请求：
```
GET http://localhost:8080/actuator/prometheus
```

响应为Prometheus格式的指标数据，包括：
```
# HELP sensor_websocket_connections 当前WebSocket连接数
# TYPE sensor_websocket_connections gauge
sensor_websocket_connections{application="flink-sensor-simulator",host="server1"} 3.0
# HELP sensor_cache_items 缓存项数量
# TYPE sensor_cache_items gauge
sensor_cache_items{application="flink-sensor-simulator",host="server1"} 5.0
# HELP sensor_queue_size 消息队列大小
# TYPE sensor_queue_size gauge
sensor_queue_size{application="flink-sensor-simulator",host="server1"} 15.0
# HELP sensor_exceptions 异常计数
# TYPE sensor_exceptions gauge
sensor_exceptions{application="flink-sensor-simulator",host="server1",type="IOException"} 12.0
sensor_exceptions{application="flink-sensor-simulator",host="server1",type="IllegalArgumentException"} 3.0
```

## 在Grafana中可视化监控数据

您可以将Prometheus与Grafana集成，创建仪表板来可视化监控数据。以下是使用Grafana创建仪表板的基本步骤：

1. 将Prometheus配置为从`/actuator/prometheus`端点抓取指标
2. 在Grafana中添加Prometheus数据源
3. 创建新的仪表板，添加以下面板：
   - WebSocket连接数图表
   - 缓存项数量和最后更新时间指标
   - 消息队列大小和使用率进度条
   - 异常类型分布饼图
   
## 监控告警设置

可以在Prometheus中配置以下告警规则：

1. WebSocket连接数过多
```
alert: TooManyWebSocketConnections
expr: sensor_websocket_connections > 1000
for: 5m
```

2. 消息队列积压
```
alert: MessageQueueBackpressure
expr: sensor_queue_size / 100 > 0.8
for: 2m
```

3. 异常次数过多
```
alert: TooManyExceptions
expr: sum(increase(sensor_exceptions[5m])) > 50
for: 1m
```

---

使用这些监控端点和指标，您可以全面监控传感器应用的运行状态，及时发现和解决潜在问题。 