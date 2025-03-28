# 传感器数据实时监控微信小程序

这是一个用于实时监控传感器数据的微信小程序页面，通过WebSocket连接接收传感器数据并使用ECharts绘制折线图。

## 功能特点

- 显示WebSocket连接状态（绿色/红色徽章）
- 使用ECharts绘制传感器数据实时折线图
- 自动处理多设备数据，为每个设备绘制不同的折线
- 实现30秒心跳机制，保持连接稳定
- 自动重连机制（最多5次，使用指数退避算法）
- 优雅地处理各种错误情况
- 显示最新的传感器数据列表

## 文件结构

- `sensor-chart.wxml`: 页面布局模板
- `sensor-chart.wxss`: 页面样式
- `sensor-chart.js`: 页面逻辑
- `sensor-chart.json`: 页面配置

## 使用前提

1. 您需要安装ECharts组件
   - 在微信小程序项目中创建`ec-canvas`目录
   - 安装[ECharts-for-WeChat](https://github.com/ecomfe/echarts-for-weixin)组件

2. 配置WebSocket服务器地址
   - 在`sensor-chart.js`文件中修改`WS_URL`常量为您的WebSocket服务器地址

## 安装步骤

1. 将这四个文件复制到您的微信小程序项目中的某个页面目录下（如`pages/sensor/`）

2. 在`app.json`中添加页面路径：

```json
{
  "pages": [
    "pages/sensor/sensor-chart"
    // ... 其他页面
  ]
}
```

3. 确保已安装ECharts组件（参考"使用前提"）

## 数据格式

页面期望接收以下格式的JSON数据：

```json
{
  "deviceId": "device_1",
  "timestamp": 1648738245000,
  "value": 23.45
}
```

## 自定义配置

您可以在`sensor-chart.js`文件中修改以下常量来自定义行为：

- `WS_URL`: WebSocket服务器地址
- `HEARTBEAT_INTERVAL`: 心跳间隔时间（默认30秒）
- `MAX_RECONNECT_ATTEMPTS`: 最大重连尝试次数（默认5次）
- `MAX_DATA_POINTS`: 每个设备保留的最大数据点数量（默认200个）
- `MAX_LATEST_DATA`: 显示在最新数据列表中的数据条数（默认5条）

## 注意事项

1. 图表初始化需要一定时间，某些情况下可能需要接收到数据后才能正确显示
2. 页面卸载时会自动关闭WebSocket连接
3. 页面隐藏时不会关闭连接，以保持数据采集
4. 如果需要修改图表样式，可以在`initChart`函数中修改ECharts配置
5. 该页面已针对多种屏幕尺寸和设备进行了适配 