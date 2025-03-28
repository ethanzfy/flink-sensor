/**
 * 传感器数据图表页面自动化测试脚本
 * 用于测试微信小程序页面在各种条件下的行为
 */

// 测试配置
const config = {
  mockServerUrl: 'ws://localhost:8080/wx-socket',
  testTimeout: 30000, // 30秒超时
  deviceSizes: [
    { name: '小屏手机', width: 320, height: 568 },
    { name: '中屏手机', width: 375, height: 667 },
    { name: '大屏手机', width: 414, height: 896 },
    { name: '平板设备', width: 768, height: 1024 }
  ],
  batteryLevels: [100, 50, 20, 10, 5]
};

// Mock数据生成器
const mockDataGenerator = {
  /**
   * 生成模拟传感器数据
   */
  generateSensorData(deviceId = 'device_1') {
    return {
      deviceId: deviceId,
      timestamp: Date.now(),
      value: 20 + Math.random() * 10
    };
  },
  
  /**
   * 生成一批测试数据
   */
  generateBatchData(count = 10, deviceCount = 3) {
    const result = [];
    for (let i = 0; i < count; i++) {
      for (let d = 1; d <= deviceCount; d++) {
        result.push(this.generateSensorData(`device_${d}`));
      }
    }
    return result;
  }
};

// 性能监测工具
const performanceMonitor = {
  metrics: {},
  
  /**
   * 开始性能计时
   */
  startTimer(name) {
    if (!this.metrics[name]) {
      this.metrics[name] = {};
    }
    this.metrics[name].startTime = Date.now();
    console.log(`[性能] 开始计时: ${name}`);
  },
  
  /**
   * 结束性能计时并记录
   */
  endTimer(name) {
    if (this.metrics[name] && this.metrics[name].startTime) {
      const duration = Date.now() - this.metrics[name].startTime;
      this.metrics[name].duration = duration;
      this.metrics[name].endTime = Date.now();
      console.log(`[性能] ${name}: ${duration}ms`);
      return duration;
    }
    return 0;
  },
  
  /**
   * 记录内存使用
   */
  recordMemoryUsage(name) {
    if (wx.getPerformance && wx.getPerformance().getMemoryStats) {
      const memStats = wx.getPerformance().getMemoryStats();
      if (!this.metrics[name]) {
        this.metrics[name] = {};
      }
      this.metrics[name].memory = memStats;
      console.log(`[性能] ${name} 内存: ${JSON.stringify(memStats)}`);
    } else {
      console.warn('[性能] 内存统计不可用');
    }
  },
  
  /**
   * 重置所有指标
   */
  reset() {
    this.metrics = {};
  },
  
  /**
   * 获取所有性能指标
   */
  getAllMetrics() {
    return this.metrics;
  }
};

// 网络模拟器
const networkSimulator = {
  /**
   * 模拟网络断开
   */
  simulateOffline() {
    // 使用微信提供的网络请求拦截
    wx.onNetworkStatusChange(function(res) {
      console.log(`[网络] 网络类型: ${res.networkType}, 已连接: ${res.isConnected}`);
    });
    
    // 设置所有请求为失败
    wx.request = function(options) {
      setTimeout(() => {
        if (options.fail) {
          options.fail({ errMsg: "request:fail 模拟网络断开" });
        }
      }, 100);
    };
    
    console.log('[网络] 已模拟网络断开');
    return true;
  },
  
  /**
   * 模拟网络恢复
   */
  simulateOnline() {
    // 恢复原始的网络请求
    delete wx.request;
    console.log('[网络] 已模拟网络恢复');
    return true;
  },
  
  /**
   * 模拟网络延迟
   */
  simulateNetworkLatency(minLatency = 200, maxLatency = 1000) {
    const originalRequest = wx.request;
    wx.request = function(options) {
      const delay = Math.floor(Math.random() * (maxLatency - minLatency)) + minLatency;
      setTimeout(() => {
        originalRequest(options);
      }, delay);
    };
    
    console.log(`[网络] 已模拟网络延迟: ${minLatency}-${maxLatency}ms`);
    return true;
  }
};

// 设备模拟器
const deviceSimulator = {
  /**
   * 模拟不同屏幕尺寸
   */
  simulateScreenSize(width, height) {
    // 模拟设备信息
    const originalGetSystemInfo = wx.getSystemInfo;
    wx.getSystemInfo = function(options) {
      originalGetSystemInfo({
        success: function(res) {
          res.windowWidth = width;
          res.windowHeight = height;
          res.screenWidth = width;
          res.screenHeight = height;
          if (options.success) {
            options.success(res);
          }
        },
        fail: options.fail
      });
    };
    
    console.log(`[设备] 已模拟屏幕尺寸: ${width}x${height}`);
    return { width, height };
  },
  
  /**
   * 模拟电池电量
   */
  simulateBatteryLevel(level) {
    const originalGetBatteryInfo = wx.getBatteryInfo;
    wx.getBatteryInfo = function(options) {
      if (options.success) {
        options.success({
          level: level,
          isCharging: level < 20 ? false : true
        });
      }
    };
    
    // 如果电量低，触发省电模式
    const lowPowerMode = level <= 10;
    
    console.log(`[设备] 已模拟电池电量: ${level}%${lowPowerMode ? ' (低电量模式)' : ''}`);
    return { level, lowPowerMode };
  },
  
  /**
   * 模拟应用进入前台
   */
  simulateForeground() {
    if (typeof wx.onAppShow === 'function') {
      // 触发小程序显示事件
      const appShowEvent = { scene: 1001 };
      wx.onAppShow(appShowEvent);
      console.log('[设备] 已模拟应用进入前台');
    }
    return true;
  },
  
  /**
   * 模拟应用进入后台
   */
  simulateBackground() {
    if (typeof wx.onAppHide === 'function') {
      // 触发小程序隐藏事件
      wx.onAppHide({});
      console.log('[设备] 已模拟应用进入后台');
    }
    return true;
  }
};

// 测试用例
const testSuite = {
  /**
   * 测试断网后恢复连接
   */
  async testNetworkRecovery(page) {
    const testName = '断网恢复测试';
    console.log(`[测试] 开始: ${testName}`);
    performanceMonitor.reset();
    
    try {
      // 首先确保页面加载完成
      await page.waitForSelector('.chart-container', { timeout: config.testTimeout });
      console.log('[测试] 图表容器已加载');
      
      // 记录初始连接状态
      const initialConnectionState = await page.evaluate(() => {
        return !!document.querySelector('.status-badge.connected');
      });
      console.log(`[测试] 初始连接状态: ${initialConnectionState ? '已连接' : '未连接'}`);
      
      // 模拟网络断开
      await page.evaluate(() => {
        return networkSimulator.simulateOffline();
      });
      performanceMonitor.startTimer('network_disconnect');
      
      // 等待连接状态变为未连接
      await page.waitForFunction(() => {
        return !!document.querySelector('.status-badge.disconnected');
      }, { timeout: config.testTimeout });
      
      const disconnectTime = performanceMonitor.endTimer('network_disconnect');
      console.log(`[测试] 检测到断网，用时: ${disconnectTime}ms`);
      
      // 记录错误消息
      const errorMsg = await page.evaluate(() => {
        const errorElement = document.querySelector('.error-message');
        return errorElement ? errorElement.textContent : null;
      });
      console.log(`[测试] 断网错误消息: ${errorMsg || '无'}`);
      
      // 模拟网络恢复
      await page.evaluate(() => {
        return networkSimulator.simulateOnline();
      });
      performanceMonitor.startTimer('network_reconnect');
      
      // 点击重试按钮
      await page.evaluate(() => {
        const retryButton = document.querySelector('.retry-button');
        if (retryButton) {
          retryButton.click();
        }
      });
      
      // 等待连接状态恢复
      await page.waitForFunction(() => {
        return !!document.querySelector('.status-badge.connected');
      }, { timeout: config.testTimeout });
      
      const reconnectTime = performanceMonitor.endTimer('network_reconnect');
      console.log(`[测试] 网络恢复连接，用时: ${reconnectTime}ms`);
      
      // 验证数据是否继续更新
      const initialDataCount = await page.evaluate(() => {
        return document.querySelectorAll('.data-item').length;
      });
      
      // 等待5秒看是否有新数据
      await page.waitFor(5000);
      
      const newDataCount = await page.evaluate(() => {
        return document.querySelectorAll('.data-item').length;
      });
      
      const dataUpdated = newDataCount > initialDataCount;
      console.log(`[测试] 恢复后数据是否更新: ${dataUpdated}, 初始: ${initialDataCount}, 当前: ${newDataCount}`);
      
      return {
        name: testName,
        success: dataUpdated,
        metrics: {
          disconnectTime,
          reconnectTime,
          initialDataCount,
          newDataCount
        }
      };
      
    } catch (error) {
      console.error(`[测试] ${testName} 失败:`, error);
      return {
        name: testName,
        success: false,
        error: error.message
      };
    }
  },
  
  /**
   * 测试不同设备分辨率
   */
  async testDeviceResolutions(page) {
    const testName = '设备分辨率适配测试';
    console.log(`[测试] 开始: ${testName}`);
    performanceMonitor.reset();
    
    const results = [];
    
    try {
      // 测试每种设备尺寸
      for (const device of config.deviceSizes) {
        console.log(`[测试] 测试设备: ${device.name} (${device.width}x${device.height})`);
        
        // 模拟设备屏幕尺寸
        await page.evaluate((deviceSize) => {
          return deviceSimulator.simulateScreenSize(deviceSize.width, deviceSize.height);
        }, device);
        
        // 模拟页面刷新
        await page.reload({ waitUntil: 'networkidle0' });
        
        // 等待图表加载
        await page.waitForSelector('.chart-container', { timeout: config.testTimeout });
        performanceMonitor.startTimer(`load_${device.name}`);
        
        // 等待图表渲染完成
        await page.waitForFunction(() => {
          return !!document.querySelector('.chart-container canvas');
        }, { timeout: config.testTimeout });
        
        const loadTime = performanceMonitor.endTimer(`load_${device.name}`);
        
        // 检查UI元素是否正确适配
        const uiCheck = await page.evaluate(() => {
          // 获取关键UI元素尺寸
          const chartContainer = document.querySelector('.chart-container');
          const statusBadge = document.querySelector('.status-badge');
          const dataList = document.querySelector('.data-list');
          
          if (!chartContainer || !statusBadge || !dataList) {
            return { success: false, error: '无法找到所有UI元素' };
          }
          
          // 获取元素尺寸
          const chartRect = chartContainer.getBoundingClientRect();
          const statusRect = statusBadge.getBoundingClientRect();
          const dataListRect = dataList.getBoundingClientRect();
          
          // 验证元素在视口内且尺寸合理
          const windowWidth = window.innerWidth;
          const windowHeight = window.innerHeight;
          
          const isChartVisible = chartRect.width > 0 && chartRect.height > 0 &&
                               chartRect.right <= windowWidth && chartRect.bottom <= windowHeight;
          
          const isDataListVisible = dataListRect.width > 0 && dataListRect.height > 0 &&
                                  dataListRect.right <= windowWidth && dataListRect.bottom <= windowHeight;
          
          const chartAspectRatio = chartRect.width / chartRect.height;
          const isAspectRatioGood = chartAspectRatio > 1 && chartAspectRatio < 3; // 合理的宽高比
          
          return {
            success: isChartVisible && isDataListVisible && isAspectRatioGood,
            chart: {
              width: chartRect.width,
              height: chartRect.height,
              aspectRatio: chartAspectRatio,
              isVisible: isChartVisible
            },
            dataList: {
              width: dataListRect.width,
              height: dataListRect.height,
              isVisible: isDataListVisible
            }
          };
        });
        
        // 截图保存（如果在实际环境中）
        // await page.screenshot({ path: `screenshot-${device.name}.png` });
        
        // 记录结果
        results.push({
          device: device.name,
          resolution: `${device.width}x${device.height}`,
          loadTime,
          uiAdaptation: uiCheck
        });
        
        console.log(`[测试] ${device.name} 结果: 加载时间=${loadTime}ms, UI适配=${uiCheck.success}`);
      }
      
      return {
        name: testName,
        success: results.every(r => r.uiAdaptation.success),
        results
      };
      
    } catch (error) {
      console.error(`[测试] ${testName} 失败:`, error);
      return {
        name: testName,
        success: false,
        error: error.message,
        results
      };
    }
  },
  
  /**
   * 测试应用前后台切换稳定性
   */
  async testBackgroundForegroundSwitch(page) {
    const testName = '前后台切换稳定性测试';
    console.log(`[测试] 开始: ${testName}`);
    performanceMonitor.reset();
    
    try {
      // 确保页面已加载
      await page.waitForSelector('.chart-container', { timeout: config.testTimeout });
      
      // 记录初始数据
      const initialData = await page.evaluate(() => {
        const chartInstance = document.querySelector('#sensor-chart')._chart;
        return chartInstance ? chartInstance.getOption() : null;
      });
      
      if (!initialData) {
        throw new Error('无法获取初始图表数据');
      }
      
      console.log('[测试] 已获取初始图表数据');
      
      // 模拟5次前后台切换
      const switchResults = [];
      for (let i = 1; i <= 5; i++) {
        console.log(`[测试] 执行第 ${i} 次前后台切换`);
        
        // 模拟进入后台
        await page.evaluate(() => {
          performanceMonitor.startTimer('background_switch');
          return deviceSimulator.simulateBackground();
        });
        
        // 等待2秒
        await page.waitFor(2000);
        
        // 模拟回到前台
        await page.evaluate(() => {
          const bgTime = performanceMonitor.endTimer('background_switch');
          performanceMonitor.startTimer('foreground_switch');
          deviceSimulator.simulateForeground();
          return bgTime;
        });
        
        // 等待页面恢复
        await page.waitFor(2000);
        
        const fgTime = await page.evaluate(() => {
          return performanceMonitor.endTimer('foreground_switch');
        });
        
        // 检查WebSocket连接是否仍然活跃
        const isConnected = await page.evaluate(() => {
          return !!document.querySelector('.status-badge.connected');
        });
        
        // 验证数据是否继续更新
        await page.waitFor(3000); // 等待数据更新
        
        const dataUpdated = await page.evaluate(() => {
          const lastUpdateElement = document.querySelector('.connection-info')
            .textContent.match(/最新数据时间: (.+)/);
          return lastUpdateElement ? lastUpdateElement[1] !== '暂无数据' : false;
        });
        
        console.log(`[测试] 切换 ${i} 结果: 连接状态=${isConnected}, 数据更新=${dataUpdated}`);
        
        switchResults.push({
          switchNumber: i,
          backgroundTime: await page.evaluate(() => performanceMonitor.metrics.background_switch?.duration || 0),
          foregroundTime: fgTime,
          isConnected,
          dataUpdated
        });
      }
      
      // 最终连接状态检查
      const finalConnectionState = await page.evaluate(() => {
        return {
          isConnected: !!document.querySelector('.status-badge.connected'),
          errorMsg: document.querySelector('.error-message')?.textContent || null
        };
      });
      
      return {
        name: testName,
        success: switchResults.every(r => r.isConnected && r.dataUpdated),
        switchResults,
        finalState: finalConnectionState
      };
      
    } catch (error) {
      console.error(`[测试] ${testName} 失败:`, error);
      return {
        name: testName,
        success: false,
        error: error.message
      };
    }
  },
  
  /**
   * 测试低电量模式下的性能
   */
  async testLowBatteryPerformance(page) {
    const testName = '低电量模式性能测试';
    console.log(`[测试] 开始: ${testName}`);
    performanceMonitor.reset();
    
    const batteryResults = [];
    
    try {
      // 确保页面已加载
      await page.waitForSelector('.chart-container', { timeout: config.testTimeout });
      
      // 测试不同电量级别
      for (const batteryLevel of config.batteryLevels) {
        console.log(`[测试] 测试电池电量: ${batteryLevel}%`);
        
        // 模拟电池电量
        const batteryState = await page.evaluate((level) => {
          return deviceSimulator.simulateBatteryLevel(level);
        }, batteryLevel);
        
        // 页面刷新
        await page.reload({ waitUntil: 'networkidle0' });
        
        // 等待图表加载
        await page.waitForSelector('.chart-container', { timeout: config.testTimeout });
        performanceMonitor.startTimer(`load_battery_${batteryLevel}`);
        
        // 等待图表渲染完成
        await page.waitForFunction(() => {
          return !!document.querySelector('.chart-container canvas');
        }, { timeout: config.testTimeout });
        
        const loadTime = performanceMonitor.endTimer(`load_battery_${batteryLevel}`);
        
        // 记录内存使用
        await page.evaluate(() => {
          performanceMonitor.recordMemoryUsage(`battery_memory`);
        });
        
        // 测试数据刷新性能（接收10条数据并测量处理时间）
        performanceMonitor.startTimer(`data_update_${batteryLevel}`);
        
        // 模拟接收10条消息
        for (let i = 0; i < 10; i++) {
          await page.evaluate((deviceId) => {
            // 模拟WebSocket消息
            const mockEvent = {
              data: JSON.stringify(mockDataGenerator.generateSensorData(deviceId))
            };
            
            // 如果存在WebSocket实例，调用其onmessage处理
            if (window.socketTask && typeof window.socketTask.onMessage === 'function') {
              window.socketTask.onMessage(mockEvent);
            }
            
          }, `device_${(i % 3) + 1}`);
          
          // 每条消息间隔100ms
          await page.waitFor(100);
        }
        
        // 等待更新完成
        await page.waitFor(1000);
        const updateTime = performanceMonitor.endTimer(`data_update_${batteryLevel}`);
        
        // 记录图表渲染性能 - 通过CSS过渡时间估计
        const renderPerformance = await page.evaluate(() => {
          // 获取渲染样式中的过渡时间
          const style = window.getComputedStyle(document.querySelector('.chart-container'));
          const transitionDuration = style.transitionDuration || '0s';
          
          // 尝试使用Performance API记录帧率
          let fps = 0;
          if (window.performance && window.performance.now) {
            const start = window.performance.now();
            let frames = 0;
            const frameCounter = () => {
              frames++;
              if (window.performance.now() - start < 1000) {
                window.requestAnimationFrame(frameCounter);
              } else {
                fps = frames;
              }
            };
            window.requestAnimationFrame(frameCounter);
          }
          
          return {
            transitionTime: transitionDuration,
            estimatedFps: fps
          };
        });
        
        // 记录电池级别测试结果
        batteryResults.push({
          batteryLevel,
          lowPowerMode: batteryState.lowPowerMode,
          loadTime,
          updateTime,
          renderPerformance,
          memoryUsage: await page.evaluate(() => performanceMonitor.metrics.battery_memory?.memory || null)
        });
        
        console.log(`[测试] 电量 ${batteryLevel}% 结果: 加载时间=${loadTime}ms, 更新时间=${updateTime}ms`);
      }
      
      // 分析不同电量对性能的影响
      const highBatteryResults = batteryResults.filter(r => r.batteryLevel >= 50);
      const lowBatteryResults = batteryResults.filter(r => r.batteryLevel < 20);
      
      const highBatteryAvgLoad = highBatteryResults.reduce((sum, r) => sum + r.loadTime, 0) / highBatteryResults.length;
      const lowBatteryAvgLoad = lowBatteryResults.reduce((sum, r) => sum + r.loadTime, 0) / lowBatteryResults.length;
      
      const highBatteryAvgUpdate = highBatteryResults.reduce((sum, r) => sum + r.updateTime, 0) / highBatteryResults.length;
      const lowBatteryAvgUpdate = lowBatteryResults.reduce((sum, r) => sum + r.updateTime, 0) / lowBatteryResults.length;
      
      const performanceImpact = {
        loadTimeDifference: `${((lowBatteryAvgLoad - highBatteryAvgLoad) / highBatteryAvgLoad * 100).toFixed(2)}%`,
        updateTimeDifference: `${((lowBatteryAvgUpdate - highBatteryAvgUpdate) / highBatteryAvgUpdate * 100).toFixed(2)}%`
      };
      
      console.log(`[测试] 低电量性能影响: 加载时间增加=${performanceImpact.loadTimeDifference}, 更新时间增加=${performanceImpact.updateTimeDifference}`);
      
      return {
        name: testName,
        success: true,
        batteryResults,
        performanceImpact
      };
      
    } catch (error) {
      console.error(`[测试] ${testName} 失败:`, error);
      return {
        name: testName,
        success: false,
        error: error.message,
        batteryResults
      };
    }
  }
};

// 测试运行器
const TestRunner = {
  /**
   * 运行所有测试
   */
  async runAllTests(page) {
    console.log('[测试] 开始运行所有测试用例');
    const results = {};
    
    try {
      // 网络恢复测试
      results.networkRecovery = await testSuite.testNetworkRecovery(page);
      
      // 设备分辨率测试
      results.deviceResolutions = await testSuite.testDeviceResolutions(page);
      
      // 前后台切换测试
      results.backgroundForeground = await testSuite.testBackgroundForegroundSwitch(page);
      
      // 低电量模式测试
      results.lowBattery = await testSuite.testLowBatteryPerformance(page);
      
      // 汇总结果
      const summary = {
        total: Object.keys(results).length,
        passed: Object.values(results).filter(r => r.success).length,
        failed: Object.values(results).filter(r => !r.success).length
      };
      
      console.log(`[测试] 所有测试完成: 总共=${summary.total}, 通过=${summary.passed}, 失败=${summary.failed}`);
      
      return {
        timestamp: new Date().toISOString(),
        summary,
        results
      };
      
    } catch (error) {
      console.error('[测试] 运行测试时发生错误:', error);
      return {
        timestamp: new Date().toISOString(),
        error: error.message,
        results
      };
    }
  }
};

// 暴露测试模块
module.exports = {
  config,
  mockDataGenerator,
  performanceMonitor,
  networkSimulator,
  deviceSimulator,
  testSuite,
  TestRunner
}; 