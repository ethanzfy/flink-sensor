# ECharts移动端时间序列图表配置

本文档详细说明了为微信小程序优化的ECharts时间序列折线图配置。

## 配置特点

1. **时间序列折线图**
   - X轴使用`time`类型，支持自动处理时间戳数据
   - 设置了5秒的刻度间隔（`minInterval: 5 * 1000`）
   - 使用了平滑曲线展示数据变化趋势（`smooth: true`）

2. **Y轴自适应配置**
   - 设置`scale: true`使Y轴自适应数据范围
   - 使用`min: 'dataMin'`和`max: 'dataMax'`自动确定轴范围
   - 通过`boundaryGap: ['10%', '10%']`保留了上下10%的边距

3. **颜色渐变**
   - 使用`LinearGradient`创建从`#5470c6`到`#91cc75`的渐变
   - 为不同设备ID自动分配不同的颜色系列
   - 每条线都有半透明面积填充，增强视觉效果

4. **最后更新时间显示**
   - 在图表右上角添加最后更新时间标题
   - 每收到新数据时自动更新时间戳

5. **移动端优化**
   - 关闭动画效果（`animation: false`）提高性能
   - 使用`hideOverlap: true`避免标签重叠
   - 优化tooltip位置，避免手指遮挡内容
   - 减小图例和标签的字体大小

## 核心配置代码

### 1. 时间轴配置

```javascript
xAxis: {
  type: 'time',
  axisLabel: {
    fontSize: 10,
    hideOverlap: true,
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
}
```

### 2. Y轴自适应配置

```javascript
yAxis: {
  type: 'value',
  name: '数值',
  scale: true, // 自适应数据范围
  min: 'dataMin', // 最小值从数据中取得
  max: 'dataMax', // 最大值从数据中取得
  boundaryGap: ['10%', '10%'], // 上下保留10%边距
  // ...其他配置
}
```

### 3. 渐变色生成

```javascript
function createGradient(chart, deviceId) {
  // 根据设备ID生成不同色系的渐变色
  // ...计算逻辑
  
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
```

### 4. 系列配置

```javascript
series: [{
  name: deviceId,
  type: 'line',
  smooth: true, // 平滑曲线
  symbol: 'circle',
  symbolSize: 4, // 减小数据点大小，优化移动端显示
  sampling: 'average', // 数据采样，优化性能
  areaStyle: {
    color: createGradient(this.chart, deviceId),
    opacity: 0.3 // 半透明填充
  },
  itemStyle: {
    color: createGradient(this.chart, deviceId)
  },
  // ...其他配置
}]
```

## 使用说明

1. **数据格式要求**：
   时间序列数据格式应为 `[时间戳, 数值]`，例如：
   ```javascript
   data: [
     [1648738240000, 23.45],
     [1648738245000, 24.01],
     // ...
   ]
   ```

2. **自定义颜色**：
   可以通过修改`createGradient`函数中的色值来自定义渐变色

3. **性能优化提示**：
   - 当数据量较大时，可以增加`sampling`采样力度
   - 可以考虑减少保留的数据点数量（MAX_DATA_POINTS）
   - 在极端情况下可以考虑关闭`smooth`平滑效果

4. **自适应配置**：
   图表会自动根据数据范围调整Y轴，无需手动设置 