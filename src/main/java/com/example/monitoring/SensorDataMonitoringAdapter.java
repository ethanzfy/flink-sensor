package com.example.monitoring;

import com.example.SensorData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Deque;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 传感器数据监控适配器
 * 用于监控传感器数据流、缓存和队列状态
 */
@Component
public class SensorDataMonitoringAdapter {
    private static final Logger logger = LoggerFactory.getLogger(SensorDataMonitoringAdapter.class);
    
    private final SensorMetricsService metricsService;
    private final DataCacheEndpoint dataCacheEndpoint;
    private final MessageQueueEndpoint messageQueueEndpoint;
    private final ExceptionStatsEndpoint exceptionStatsEndpoint;
    
    // 设备数据缓存，保存最新的数据
    private final Map<String, SensorData> latestDataByDevice = new ConcurrentHashMap<>();
    
    // 最近处理的数据记录
    private final Deque<SensorData> recentDataRecords = new LinkedList<>();
    private static final int MAX_RECENT_RECORDS = 100;
    
    // 模拟消息队列
    private final LinkedBlockingQueue<SensorData> messageQueue = new LinkedBlockingQueue<>(1000);
    private final AtomicInteger processedCount = new AtomicInteger(0);
    
    public SensorDataMonitoringAdapter(
            SensorMetricsService metricsService,
            DataCacheEndpoint dataCacheEndpoint,
            MessageQueueEndpoint messageQueueEndpoint,
            ExceptionStatsEndpoint exceptionStatsEndpoint) {
        this.metricsService = metricsService;
        this.dataCacheEndpoint = dataCacheEndpoint;
        this.messageQueueEndpoint = messageQueueEndpoint;
        this.exceptionStatsEndpoint = exceptionStatsEndpoint;
    }
    
    /**
     * 处理传感器数据
     */
    public void processSensorData(SensorData sensorData) {
        try {
            // 1. 更新最新数据缓存
            latestDataByDevice.put(sensorData.deviceId(), sensorData);
            dataCacheEndpoint.updateCacheItem(sensorData.deviceId(), sensorData);
            
            // 2. 添加到最近处理的数据记录
            synchronized (recentDataRecords) {
                recentDataRecords.addFirst(sensorData);
                if (recentDataRecords.size() > MAX_RECENT_RECORDS) {
                    recentDataRecords.removeLast();
                }
            }
            
            // 3. 模拟队列处理（添加到队列）
            if (messageQueue.offer(sensorData)) {
                // 更新队列大小
                messageQueueEndpoint.updateQueueSize(messageQueue.size());
            } else {
                // 队列已满，记录异常
                logger.warn("消息队列已满，传感器数据被丢弃: {}", sensorData.deviceId());
                exceptionStatsEndpoint.recordException(
                        "QueueFullException",
                        "消息队列已满，数据被丢弃: " + sensorData.deviceId(),
                        null
                );
            }
            
            // 4. 模拟处理队列中的消息
            processQueuedMessages();
            
        } catch (Exception e) {
            // 记录异常
            logger.error("处理传感器数据时出错: {}", e.getMessage(), e);
            exceptionStatsEndpoint.recordException(
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    getStackTraceAsString(e)
            );
        }
    }
    
    /**
     * 模拟处理队列中的消息
     */
    private void processQueuedMessages() {
        // 随机处理一些消息，模拟异步处理
        int toProcess = Math.min((int)(Math.random() * 5) + 1, messageQueue.size());
        
        for (int i = 0; i < toProcess; i++) {
            SensorData data = messageQueue.poll();
            if (data != null) {
                // 模拟处理
                processedCount.incrementAndGet();
                messageQueueEndpoint.recordProcessedMessage();
            } else {
                break;
            }
        }
        
        // 更新队列大小
        messageQueueEndpoint.updateQueueSize(messageQueue.size());
    }
    
    /**
     * 获取堆栈跟踪字符串
     */
    private String getStackTraceAsString(Throwable throwable) {
        if (throwable == null) return null;
        
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append(element.toString()).append("\n");
        }
        return sb.toString();
    }
    
    /**
     * 获取最近处理的数据
     */
    public Deque<SensorData> getRecentData() {
        return new LinkedList<>(recentDataRecords);
    }
    
    /**
     * 获取最新的设备数据
     */
    public Map<String, SensorData> getLatestDataByDevice() {
        return new ConcurrentHashMap<>(latestDataByDevice);
    }
    
    /**
     * 获取队列大小
     */
    public int getQueueSize() {
        return messageQueue.size();
    }
    
    /**
     * 获取已处理消息数
     */
    public int getProcessedCount() {
        return processedCount.get();
    }
} 