package com.example;

import com.example.monitoring.SensorDataMonitoringAdapter;
import com.example.websocket.SensorWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * WebSocket传感器服务类
 * 负责将传感器数据发送到WebSocket客户端
 */
@Service
public class WebSocketSensorService {
    private static final Logger LOG = LoggerFactory.getLogger(WebSocketSensorService.class);
    
    private final SensorWebSocketHandler webSocketHandler;
    private final SensorDataMonitoringAdapter monitoringAdapter;
    
    @Autowired
    public WebSocketSensorService(SensorWebSocketHandler webSocketHandler, 
                                 SensorDataMonitoringAdapter monitoringAdapter) {
        this.webSocketHandler = webSocketHandler;
        this.monitoringAdapter = monitoringAdapter;
    }
    
    /**
     * 发送传感器数据到WebSocket客户端
     */
    public void sendSensorData(SensorData sensorData) {
        // 首先更新监控指标
        monitoringAdapter.processSensorData(sensorData);
        
        // 获取当前连接的客户端数量
        int activeClients = webSocketHandler.getActiveSessionCount();
        
        if (activeClients > 0) {
            LOG.debug("发送传感器数据到 {} 个WebSocket客户端: {}, 时间: {}", 
                    activeClients, sensorData.deviceId(), sensorData.getFormattedTimestamp());
            
            // 将传感器数据广播到所有连接的客户端
            webSocketHandler.broadcastSensorData(sensorData);
        } else {
            LOG.debug("没有活跃的WebSocket客户端连接，跳过数据广播");
        }
    }
    
    /**
     * 获取最新的监控统计信息
     */
    public Object getMonitoringStats() {
        return new MonitoringStats(
                webSocketHandler.getStats(),
                monitoringAdapter.getLatestDataByDevice().size(),
                monitoringAdapter.getQueueSize(),
                monitoringAdapter.getProcessedCount()
        );
    }
    
    /**
     * 监控统计数据类
     */
    public static class MonitoringStats {
        private final Object webSocketStats;
        private final int cachedDeviceCount;
        private final int queueSize;
        private final int processedCount;
        
        public MonitoringStats(Object webSocketStats, int cachedDeviceCount, 
                              int queueSize, int processedCount) {
            this.webSocketStats = webSocketStats;
            this.cachedDeviceCount = cachedDeviceCount;
            this.queueSize = queueSize;
            this.processedCount = processedCount;
        }
        
        public Object getWebSocketStats() {
            return webSocketStats;
        }
        
        public int getCachedDeviceCount() {
            return cachedDeviceCount;
        }
        
        public int getQueueSize() {
            return queueSize;
        }
        
        public int getProcessedCount() {
            return processedCount;
        }
    }
} 