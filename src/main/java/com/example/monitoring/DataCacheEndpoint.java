package com.example.monitoring;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据缓存状态的Actuator端点
 * 提供缓存中的数据项数量和最后更新时间
 */
@Component
@Endpoint(id = "data-cache")
public class DataCacheEndpoint {

    private final SensorMetricsService metricsService;
    // 缓存的示例数据
    private final Map<String, Object> cacheItems = new ConcurrentHashMap<>();
    private long lastUpdateTime = System.currentTimeMillis();

    public DataCacheEndpoint(SensorMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @ReadOperation
    public Map<String, Object> cacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("itemCount", metricsService.getCachedItemsCount());
        stats.put("lastUpdateTime", lastUpdateTime);
        stats.put("lastUpdateTimeFormatted", new java.util.Date(lastUpdateTime).toString());
        stats.put("ageSeconds", (System.currentTimeMillis() - lastUpdateTime) / 1000.0);
        
        // 添加最近的几个数据项样本（如果有）
        if (!cacheItems.isEmpty()) {
            int sampleSize = Math.min(cacheItems.size(), 5);
            Map<String, Object> samples = new HashMap<>();
            
            cacheItems.entrySet().stream()
                    .limit(sampleSize)
                    .forEach(entry -> samples.put(entry.getKey(), entry.getValue()));
            
            stats.put("samples", samples);
        }
        
        return stats;
    }

    /**
     * 添加或更新缓存项
     */
    public void updateCacheItem(String key, Object value) {
        cacheItems.put(key, value);
        lastUpdateTime = System.currentTimeMillis();
        metricsService.updateCacheMetrics(cacheItems.size());
    }

    /**
     * 获取缓存项
     */
    public Object getCacheItem(String key) {
        return cacheItems.get(key);
    }

    /**
     * 移除缓存项
     */
    public void removeCacheItem(String key) {
        cacheItems.remove(key);
        lastUpdateTime = System.currentTimeMillis();
        metricsService.updateCacheMetrics(cacheItems.size());
    }

    /**
     * 清空缓存
     */
    public void clearCache() {
        cacheItems.clear();
        lastUpdateTime = System.currentTimeMillis();
        metricsService.updateCacheMetrics(0);
    }
} 