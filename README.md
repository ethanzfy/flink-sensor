# Flink传感器数据模拟系统

这是一个基于Apache Flink和Spring Boot的传感器数据模拟系统，用于实时生成、传输和可视化传感器数据。该系统支持多种传感器类型（温度、湿度、压力等），并提供WebSocket实时推送和RESTful API接口。

## 主要功能

- 实时模拟多种传感器数据生成
- WebSocket实时数据推送
- 基于ECharts的实时数据可视化
- 完善的监控指标和Actuator端点
- Spring Boot管理和配置

## 技术栈

- Apache Flink：流式数据处理
- Spring Boot：应用程序框架
- WebSocket：实时数据传输
- ECharts：数据可视化
- Prometheus：监控指标收集
- Docker：容器化部署

## 快速开始

### 运行应用

```bash
# 构建并运行
./run-app.sh --build --run

# 仅运行
./run-app.sh --run
```

### 访问页面

- 主页面：http://localhost:8080
- Actuator监控：http://localhost:8080/actuator
- 传感器状态：http://localhost:8080/api/simulator/status

## 配置

在`application.yml`文件中可以配置：

- 模拟器参数：数据生成间隔、传感器类型等
- 监控参数：异常阈值、队列大小阈值等
- WebSocket配置：心跳间隔、超时时间等

## 项目结构

- `src/main/java`：Java源代码
- `src/main/resources`：配置文件
- `src/test`：测试代码
- `docker`：Docker配置文件

## 作者

山西应用科技学院信息工程学院 