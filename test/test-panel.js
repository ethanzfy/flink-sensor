/**
 * 传感器数据可视化测试面板组件
 * 用于在小程序开发工具中快速运行测试
 */
const { WxappTestRunner } = require('./run-wxapp-tests');

Component({
  /**
   * 组件属性
   */
  properties: {
    // 目标页面实例，用于测试
    pageInstance: {
      type: Object,
      value: null
    }
  },

  /**
   * 组件初始数据
   */
  data: {
    isExpanded: false,
    testOptions: [
      { id: 'all', name: '运行所有测试', description: '运行全部四项自动化测试' },
      { id: 'testNetworkRecovery', name: '断网恢复测试', description: '测试网络断开后重连功能' },
      { id: 'testDeviceResolutions', name: '设备适配测试', description: '测试不同屏幕分辨率的UI适配' },
      { id: 'testBackgroundForegroundSwitch', name: '前后台切换测试', description: '测试小程序前后台切换的稳定性' },
      { id: 'testLowBatteryPerformance', name: '低电量性能测试', description: '测试低电量模式下的应用性能' }
    ],
    testInProgress: false,
    lastTestResult: null
  },

  /**
   * 组件方法
   */
  methods: {
    /**
     * 切换面板展开/收起状态
     */
    togglePanel() {
      this.setData({
        isExpanded: !this.data.isExpanded
      });
    },
    
    /**
     * 运行单个测试
     */
    async runTest(e) {
      const testId = e.currentTarget.dataset.id;
      console.log(`开始运行测试: ${testId}`);
      
      // 如果没有设置页面实例，使用当前页面
      const pageInstance = this.properties.pageInstance || this.getPageInstance();
      
      if (!pageInstance) {
        wx.showToast({
          title: '无法获取页面实例',
          icon: 'none'
        });
        return;
      }
      
      this.setData({ testInProgress: true });
      
      try {
        let result;
        if (testId === 'all') {
          // 运行所有测试
          result = await WxappTestRunner.runTests(pageInstance);
        } else {
          // 运行单个测试
          result = await WxappTestRunner.runSingleTest(pageInstance, testId);
        }
        
        this.setData({
          lastTestResult: {
            id: testId,
            timestamp: new Date().toLocaleString(),
            success: testId === 'all' ? 
              (result.summary.failed === 0) : 
              (result && result.success)
          }
        });
      } catch (error) {
        console.error('测试运行失败:', error);
        wx.showModal({
          title: '测试错误',
          content: error.message,
          showCancel: false
        });
      } finally {
        this.setData({ testInProgress: false });
      }
    },
    
    /**
     * 获取当前页面实例
     */
    getPageInstance() {
      const pages = getCurrentPages();
      return pages[pages.length - 1];
    }
  }
}); 