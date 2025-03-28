package com.example.monitoring;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 消息队列监控的Actuator端点
 * 提供队列大小和消息处理统计信息
 */
@Component
@Endpoint(id = "message-queue")
public class MessageQueueEndpoint {

    private final SensorMetricsService metricsService;
    
    @Value("${sensor.monitoring.queue-size-threshold:100}")
    private int queueSizeThreshold;

    public MessageQueueEndpoint(SensorMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @ReadOperation
    public Map<String, Object> queueStats() {
        Map<String, Object> stats = new HashMap<>();
        int currentQueueSize = metricsService.getQueueSize();
        
        stats.put("currentSize", currentQueueSize);
        stats.put("totalProcessed", metricsService.getTotalMessagesProcessed());
        stats.put("threshold", queueSizeThreshold);
        stats.put("status", getQueueStatus(currentQueueSize));
        stats.put("backpressureDetected", currentQueueSize > queueSizeThreshold);
        
        // 计算利用率百分比
        if (queueSizeThreshold > 0) {
            double utilizationPercentage = (double) currentQueueSize / queueSizeThreshold * 100;
            stats.put("utilizationPercentage", Math.min(100, utilizationPercentage));
        }
        
        return stats;
    }
    
    /**
     * 根据队列大小获取状态描述
     */
    private String getQueueStatus(int queueSize) {
        if (queueSize == 0) {
            return "IDLE";
        } else if (queueSize < queueSizeThreshold * 0.5) {
            return "NORMAL";
        } else if (queueSize < queueSizeThreshold * 0.8) {
            return "BUSY";
        } else if (queueSize < queueSizeThreshold) {
            return "WARNING";
        } else {
            return "CRITICAL";
        }
    }
    
    /**
     * 更新队列大小
     */
    public void updateQueueSize(int size) {
        metricsService.updateQueueSize(size);
    }
    
    /**
     * 记录处理的消息
     */
    public void recordProcessedMessage() {
        metricsService.incrementProcessedMessages();
    }
} 