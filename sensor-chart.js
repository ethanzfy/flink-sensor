// 引入ECharts组件
import * as echarts from '../../ec-canvas/echarts';

// WebSocket服务器地址
const WS_URL = 'wss://your-api.com/wx-socket';
// 心跳间隔（毫秒）
const HEARTBEAT_INTERVAL = 30000;
// 最大重连次数
const MAX_RECONNECT_ATTEMPTS = 5;
// 保留的最近数据点数量
const MAX_DATA_POINTS = 200;
// 保留的最新数据条数
const MAX_LATEST_DATA = 5;

// 初始化图表函数
function initChart(canvas, width, height, dpr) {
  const chart = echarts.init(canvas, null, {
    width: width,
    height: height,
    devicePixelRatio: dpr // 像素比
  });
  canvas.setChart(chart);

  // 获取当前时间作为初始显示
  const now = new Date();
  const updateTimeStr = formatTime(now);

  // 初始配置
  const option = {
    // 关闭动画，优化移动端性能
    animation: false,
    
    title: [
      {
        text: '传感器实时数据',
        left: 'center',
        textStyle: {
          fontSize: 14,
          fontWeight: 'normal'
        }
      },
      {
        id: 'updateTime',
        text: '最后更新: ' + updateTimeStr,
        textStyle: {
          fontSize: 12,
          color: '#999',
          fontWeight: 'normal'
        },
        right: 10,
        top: 0
      }
    ],
    
    tooltip: {
      trigger: 'axis',
      confine: true, // 限制在图表区域内
      position: function(point) {
        // 固定在触摸点上方显示，避免手指遮挡
        return [point[0], point[1] - 130];
      },
      formatter: function(params) {
        const param = params[0];
        return param.seriesName + '<br/>' +
               '时间: ' + param.data.formattedTime + '<br/>' +
               '数值: ' + param.data.value.toFixed(2);
      },
      textStyle: {
        fontSize: 12
      }
    },
    
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '15%',
      containLabel: true
    },
    
    xAxis: {
      type: 'time',
      axisLine: {
        lineStyle: {
          color: '#999'
        }
      },
      axisLabel: {
        fontSize: 10,
        hideOverlap: true, // 隐藏重叠标签
        formatter: function(value) {
          // 简化的时间格式，仅显示时:分:秒
          const date = new Date(value);
          const hours = date.getHours().toString().padStart(2, '0');
          const minutes = date.getMinutes().toString().padStart(2, '0');
          const seconds = date.getSeconds().toString().padStart(2, '0');
          return `${hours}:${minutes}:${seconds}`;
        }
      },
      // 添加5秒的刻度间隔
      minInterval: 5 * 1000, // 5秒，单位是毫秒
      splitLine: {
        show: false  // 不显示竖向网格线，减少视觉干扰
      }
    },
    
    yAxis: {
      type: 'value',
      name: '数值',
      scale: true, // 自适应数据范围
      min: 'dataMin', // 最小值从数据中取得
      max: 'dataMax', // 最大值从数据中取得
      boundaryGap: ['10%', '10%'], // 上下保留10%边距
      axisLine: {
        lineStyle: {
          color: '#999'
        }
      },
      axisLabel: {
        fontSize: 10,
        hideOverlap: true,
        formatter: function (value) {
          // 根据数值大小格式化，保留适当小数位
          if (Math.abs(value) >= 1000) {
            return value.toFixed(0);
          } else if (Math.abs(value) >= 100) {
            return value.toFixed(1);
          } else {
            return value.toFixed(2);
          }
        }
      },
      splitLine: {
        lineStyle: {
          color: ['#eee'],
          type: 'dashed' // 虚线网格，减轻视觉干扰
        }
      }
    },
    
    // 图例配置
    legend: {
      type: 'scroll', // 可滚动图例，支持多设备
      orient: 'horizontal',
      bottom: 0,
      textStyle: {
        fontSize: 10
      },
      pageIconSize: 12,
      pageTextStyle: {
        fontSize: 10
      },
      itemWidth: 15,
      itemHeight: 10
    },
    
    series: []
  };

  chart.setOption(option);
  return chart;
}

// 为图表创建渐变色
function createGradient(chart, deviceId) {
  // 根据设备ID生成不同色系的渐变色
  // 默认使用蓝绿渐变
  const baseColorMap = {
    default: ['#5470c6', '#91cc75']
  };
  
  // 计算每个设备的颜色
  const deviceIndex = parseInt(deviceId.replace(/[^\d]/g, '')) || 0;
  const colorIndex = deviceIndex % 5; // 最多5种不同的颜色系
  
  let colors;
  
  // 根据设备索引选择颜色系
  switch(colorIndex) {
    case 0:
      colors = ['#5470c6', '#91cc75']; // 蓝绿
      break;
    case 1:
      colors = ['#fc8452', '#ee6666']; // 橙红
      break;
    case 2:
      colors = ['#73c0de', '#3ba272']; // 浅蓝绿
      break;
    case 3:
      colors = ['#9a60b4', '#ea7ccc']; // 紫粉
      break;
    case 4:
      colors = ['#5b8ff9', '#61ddaa']; // 蓝绿变种
      break;
    default:
      colors = baseColorMap.default;
  }
  
  return new echarts.graphic.LinearGradient(0, 0, 0, 1, [
    {
      offset: 0,
      color: colors[0]
    },
    {
      offset: 1,
      color: colors[1]
    }
  ]);
}

// 格式化时间显示 HH:MM:SS
function formatTime(date) {
  const hours = date.getHours().toString().padStart(2, '0');
  const minutes = date.getMinutes().toString().padStart(2, '0');
  const seconds = date.getSeconds().toString().padStart(2, '0');
  return `${hours}:${minutes}:${seconds}`;
}

Page({
  data: {
    ec: {
      onInit: initChart
    },
    connected: false,
    deviceCount: 0,
    lastUpdateTime: '',
    errorMessage: '',
    reconnectAttempts: 0,
    latestData: [] // 最新的几条数据
  },

  // 设备数据映射，格式 {deviceId: [数据点数组]}
  deviceDataMap: {},
  // 图表实例
  chart: null,
  // WebSocket实例
  socketTask: null,
  // 心跳定时器
  heartbeatTimer: null,

  onLoad: function() {
    this.connectWebSocket();
  },

  onReady: function() {
    // 获取图表组件，便于后续更新
    this.ecComponent = this.selectComponent('#sensor-chart');
  },

  onShow: function() {
    // 页面显示时，如果连接已关闭则重新连接
    if (this.data.connected === false && this.socketTask === null) {
      this.connectWebSocket();
    }
  },

  onHide: function() {
    // 页面隐藏时不关闭连接，保持数据采集
  },

  onUnload: function() {
    // 页面卸载时关闭连接
    this.closeConnection();
  },

  // 建立WebSocket连接
  connectWebSocket: function() {
    if (this.socketTask !== null) {
      return; // 已存在连接，不重复创建
    }

    // 清空错误消息
    this.setData({
      errorMessage: ''
    });

    try {
      // 创建WebSocket连接
      this.socketTask = wx.connectSocket({
        url: WS_URL,
        success: () => {
          console.log('WebSocket连接创建成功');
        },
        fail: (err) => {
          console.error('WebSocket连接创建失败', err);
          this.handleConnectionError('连接服务器失败');
        }
      });

      // 连接打开事件
      this.socketTask.onOpen(() => {
        console.log('WebSocket连接已打开');
        this.setData({
          connected: true,
          reconnectAttempts: 0
        });
        
        // 启动心跳
        this.startHeartbeat();
      });

      // 接收消息事件
      this.socketTask.onMessage((res) => {
        try {
          if (res.data === 'ACK') {
            // 心跳响应，不处理
            console.log('收到心跳响应');
            return;
          }
          
          // 解析传感器数据
          const sensorData = JSON.parse(res.data);
          
          // 格式化时间显示
          const date = new Date(sensorData.timestamp);
          const formattedTime = this.formatDateTime(date);
          
          // 准备数据点
          const dataPoint = {
            name: sensorData.deviceId,
            value: [date, sensorData.value],
            // 附加原始数据用于tooltip等
            deviceId: sensorData.deviceId,
            timestamp: sensorData.timestamp,
            formattedTime: formattedTime
          };
          
          // 更新设备数据映射
          if (!this.deviceDataMap[sensorData.deviceId]) {
            this.deviceDataMap[sensorData.deviceId] = [];
          }
          
          // 添加数据点
          this.deviceDataMap[sensorData.deviceId].push(dataPoint);
          
          // 限制数据点数量
          if (this.deviceDataMap[sensorData.deviceId].length > MAX_DATA_POINTS) {
            this.deviceDataMap[sensorData.deviceId].shift();
          }
          
          // 更新界面数据
          this.updateUI(dataPoint);
          
          // 更新图表
          this.updateChart();
          
          // 更新图表标题中的最后更新时间
          if (this.chart) {
            const now = new Date();
            const updateTimeStr = formatTime(now);
            
            this.chart.setOption({
              title: [
                {}, // 保持第一个标题不变
                {
                  text: '最后更新: ' + updateTimeStr
                }
              ]
            });
          }
        } catch (e) {
          console.error('处理消息出错', e);
        }
      });

      // 连接关闭事件
      this.socketTask.onClose(() => {
        console.log('WebSocket连接已关闭');
        this.setData({
          connected: false
        });
        this.stopHeartbeat();
        this.socketTask = null;
        
        // 如果不是人为关闭，则尝试重连
        if (this.data.reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
          this.attemptReconnect();
        } else {
          this.setData({
            errorMessage: '连接断开，请尝试手动重连'
          });
        }
      });

      // 连接错误事件
      this.socketTask.onError((err) => {
        console.error('WebSocket连接错误', err);
        this.handleConnectionError('连接出现错误');
      });
    } catch (e) {
      console.error('创建WebSocket连接异常', e);
      this.handleConnectionError('创建连接时出现异常');
    }
  },

  // 手动重试连接
  retryConnection: function() {
    this.setData({
      reconnectAttempts: 0,
      errorMessage: ''
    });
    this.connectWebSocket();
  },

  // 尝试重新连接
  attemptReconnect: function() {
    const attempts = this.data.reconnectAttempts + 1;
    this.setData({
      reconnectAttempts: attempts,
      errorMessage: `连接断开，正在尝试重连(${attempts}/${MAX_RECONNECT_ATTEMPTS})...`
    });
    
    // 使用指数退避算法计算延迟
    const delay = Math.min(30000, 1000 * Math.pow(2, attempts - 1));
    
    setTimeout(() => {
      if (!this.data.connected) {
        this.connectWebSocket();
      }
    }, delay);
  },

  // 处理连接错误
  handleConnectionError: function(message) {
    this.setData({
      connected: false,
      errorMessage: message
    });
    this.socketTask = null;
    this.stopHeartbeat();
    
    // 尝试重连
    if (this.data.reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
      this.attemptReconnect();
    }
  },

  // 关闭连接
  closeConnection: function() {
    this.stopHeartbeat();
    
    if (this.socketTask) {
      this.socketTask.close({
        success: () => {
          console.log('WebSocket连接已关闭');
        },
        fail: (err) => {
          console.error('关闭WebSocket连接失败', err);
        },
        complete: () => {
          this.socketTask = null;
          this.setData({
            connected: false
          });
        }
      });
    }
  },

  // 启动心跳
  startHeartbeat: function() {
    this.stopHeartbeat(); // 先停止已有的心跳
    
    this.heartbeatTimer = setInterval(() => {
      if (this.socketTask && this.data.connected) {
        console.log('发送心跳');
        this.socketTask.send({
          data: 'HEARTBEAT',
          fail: (err) => {
            console.error('发送心跳失败', err);
            // 心跳发送失败，可能连接已断开
            this.handleConnectionError('心跳发送失败');
          }
        });
      } else {
        this.stopHeartbeat();
      }
    }, HEARTBEAT_INTERVAL);
  },

  // 停止心跳
  stopHeartbeat: function() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  },

  // 更新UI数据
  updateUI: function(dataPoint) {
    // 更新设备数量
    const deviceCount = Object.keys(this.deviceDataMap).length;
    
    // 更新最新数据时间
    const lastUpdateTime = dataPoint.formattedTime;
    
    // 更新最新数据列表
    let latestData = this.data.latestData.slice();
    
    // 检查是否已存在相同设备的数据
    const existingIndex = latestData.findIndex(item => item.deviceId === dataPoint.deviceId);
    
    if (existingIndex !== -1) {
      // 更新已有设备的数据
      latestData[existingIndex] = dataPoint;
    } else {
      // 添加新设备的数据
      latestData.unshift(dataPoint);
      
      // 限制列表长度
      if (latestData.length > MAX_LATEST_DATA) {
        latestData = latestData.slice(0, MAX_LATEST_DATA);
      }
    }
    
    // 更新界面
    this.setData({
      deviceCount,
      lastUpdateTime,
      latestData
    });
  },

  // 更新图表
  updateChart: function() {
    if (!this.ecComponent) {
      return;
    }
    
    this.ecComponent.init((canvas, width, height, dpr) => {
      // 检查图表实例是否已存在
      if (!this.chart) {
        this.chart = initChart(canvas, width, height, dpr);
      }
      
      // 提取所有设备系列数据
      const series = [];
      
      Object.keys(this.deviceDataMap).forEach(deviceId => {
        series.push({
          name: deviceId,
          type: 'line',
          smooth: true, // 平滑曲线
          symbol: 'circle',
          symbolSize: 4, // 减小数据点大小，优化移动端显示
          sampling: 'average', // 数据采样，优化性能
          // 使用渐变色
          areaStyle: {
            color: createGradient(this.chart, deviceId),
            opacity: 0.3 // 半透明填充
          },
          itemStyle: {
            color: createGradient(this.chart, deviceId)
          },
          lineStyle: {
            width: 2
          },
          showSymbol: false, // 默认不显示拐点，交互时显示
          emphasis: {
            // 高亮时的样式
            itemStyle: {
              shadowBlur: 10,
              shadowColor: 'rgba(0, 0, 0, 0.3)'
            }
          },
          data: this.deviceDataMap[deviceId].map(item => ({
            name: item.deviceId,
            value: item.value,
            deviceId: item.deviceId,
            timestamp: item.timestamp,
            formattedTime: item.formattedTime
          }))
        });
      });
      
      // 更新图表配置
      this.chart.setOption({
        series: series
      });
      
      return this.chart;
    });
  },

  // 格式化日期时间
  formatDateTime: function(date) {
    const year = date.getFullYear();
    const month = (date.getMonth() + 1).toString().padStart(2, '0');
    const day = date.getDate().toString().padStart(2, '0');
    const hours = date.getHours().toString().padStart(2, '0');
    const minutes = date.getMinutes().toString().padStart(2, '0');
    const seconds = date.getSeconds().toString().padStart(2, '0');
    
    return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
  }
}); 