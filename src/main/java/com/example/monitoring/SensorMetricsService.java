package com.example.monitoring;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 传感器数据监控指标服务
 * 收集WebSocket连接、数据缓存、消息队列和异常等监控指标
 */
@Service
public class SensorMetricsService implements MeterBinder {
    private static final Logger logger = LoggerFactory.getLogger(SensorMetricsService.class);

    private final MeterRegistry registry;

    // WebSocket连接计数器
    private final AtomicInteger activeConnections = new AtomicInteger(0);

    // 数据缓存指标
    private final AtomicInteger cachedItemsCount = new AtomicInteger(0);
    private long lastCacheUpdateTimestamp = System.currentTimeMillis();

    // 消息队列指标
    private final AtomicInteger queueSize = new AtomicInteger(0);
    private final AtomicLong totalMessagesProcessed = new AtomicLong(0);

    // 异常统计
    private final Map<String, AtomicInteger> exceptionCounters = new ConcurrentHashMap<>();
    private final Map<String, Gauge> exceptionGauges = new ConcurrentHashMap<>();

    // 监控阈值配置
    @Value("${sensor.monitoring.exception-threshold:10}")
    private int exceptionThreshold;

    @Value("${sensor.monitoring.queue-size-threshold:100}")
    private int queueSizeThreshold;

    public SensorMetricsService(MeterRegistry registry) {
        this.registry = registry;
        
        // 注册主要指标
        registry.gauge("sensor.websocket.connections", activeConnections);
        registry.gauge("sensor.cache.items", cachedItemsCount);
        registry.gauge("sensor.queue.size", queueSize);
        registry.gauge("sensor.cache.last_update_seconds", this::getCacheLastUpdateSeconds);
        
        Counter.builder("sensor.messages.processed.total")
                .description("传感器数据处理总数")
                .register(registry);
        
        Timer.builder("sensor.data.processing.time")
                .description("传感器数据处理时间")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        // 已在构造函数中实现绑定
    }

    /**
     * WebSocket连接相关方法
     */
    public void incrementConnections() {
        activeConnections.incrementAndGet();
        logger.debug("WebSocket连接数增加，当前连接数: {}", activeConnections.get());
    }

    public void decrementConnections() {
        activeConnections.decrementAndGet();
        logger.debug("WebSocket连接数减少，当前连接数: {}", activeConnections.get());
    }

    public int getActiveConnectionsCount() {
        return activeConnections.get();
    }

    /**
     * 数据缓存相关方法
     */
    public void updateCacheMetrics(int itemsCount) {
        cachedItemsCount.set(itemsCount);
        lastCacheUpdateTimestamp = System.currentTimeMillis();
    }

    private double getCacheLastUpdateSeconds() {
        return (System.currentTimeMillis() - lastCacheUpdateTimestamp) / 1000.0;
    }

    public int getCachedItemsCount() {
        return cachedItemsCount.get();
    }

    /**
     * 消息队列相关方法
     */
    public void updateQueueSize(int size) {
        queueSize.set(size);
        
        // 队列积压预警
        if (size > queueSizeThreshold) {
            logger.warn("消息队列积压超过阈值! 当前大小: {}, 阈值: {}", size, queueSizeThreshold);
        }
    }

    public void incrementProcessedMessages() {
        totalMessagesProcessed.incrementAndGet();
        Counter counter = registry.counter("sensor.messages.processed.total");
        counter.increment();
    }

    public int getQueueSize() {
        return queueSize.get();
    }

    public long getTotalMessagesProcessed() {
        return totalMessagesProcessed.get();
    }

    /**
     * 异常统计相关方法
     */
    public void recordException(String exceptionType) {
        exceptionCounters.computeIfAbsent(exceptionType, k -> {
            AtomicInteger counter = new AtomicInteger(0);
            // 为每种异常类型创建一个gauge
            if (!exceptionGauges.containsKey(exceptionType)) {
                Gauge gauge = Gauge.builder("sensor.exceptions", counter, AtomicInteger::get)
                        .tag("type", exceptionType)
                        .description("传感器应用异常计数")
                        .register(registry);
                exceptionGauges.put(exceptionType, gauge);
            }
            return counter;
        }).incrementAndGet();
        
        // 异常预警
        int count = exceptionCounters.get(exceptionType).get();
        if (count % exceptionThreshold == 0) {
            logger.warn("异常计数达到阈值! 类型: {}, 计数: {}", exceptionType, count);
        }
    }

    public Map<String, Integer> getExceptionCounts() {
        return exceptionCounters.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
    }

    /**
     * 计时器工具方法
     */
    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public long stopTimer(Timer.Sample sample, String name, String... tags) {
        Timer timer = Timer.builder(name)
                .tags(tags)
                .register(registry);
        return sample.stop(timer);
    }

    /**
     * 定期清理过期指标
     */
    @Scheduled(fixedRateString = "${sensor.monitoring.metrics-collection-interval:60}000")
    public void periodicMetricsCollection() {
        logger.debug("执行定期指标收集: 连接数={}, 缓存项数={}, 队列大小={}, 处理消息总数={}",
                activeConnections.get(), cachedItemsCount.get(), queueSize.get(), totalMessagesProcessed.get());
        
        // 每分钟输出累计异常数
        if (!exceptionCounters.isEmpty()) {
            logger.info("累计异常统计: {}", 
                    exceptionCounters.entrySet().stream()
                            .map(e -> e.getKey() + "=" + e.getValue().get())
                            .collect(Collectors.joining(", ")));
        }
    }
    
    /**
     * 记录SensorData值的分布
     */
    public void recordSensorValue(double value) {
        registry.summary("sensor.data.value").record(value);
        DistributionSummary.builder("sensor.data.value.distribution")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .register(registry)
                .record(value);
    }
} 