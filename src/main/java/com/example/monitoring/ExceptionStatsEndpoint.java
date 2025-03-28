package com.example.monitoring;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 异常统计的Actuator端点
 * 按异常类型分类统计异常次数
 */
@Component
@Endpoint(id = "exception-stats")
public class ExceptionStatsEndpoint {

    private final SensorMetricsService metricsService;
    private final List<ExceptionRecord> recentExceptions = new ArrayList<>();
    private static final int MAX_RECENT_EXCEPTIONS = 20; // 保留最近20条异常记录

    public ExceptionStatsEndpoint(SensorMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @ReadOperation
    public Map<String, Object> exceptionStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // 获取所有异常计数
        Map<String, Integer> exceptionCounts = metricsService.getExceptionCounts();
        stats.put("counts", exceptionCounts);
        
        // 总异常计数
        int totalExceptions = exceptionCounts.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        stats.put("totalCount", totalExceptions);
        
        // 异常分类占比
        if (totalExceptions > 0) {
            Map<String, Double> percentages = exceptionCounts.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> (double) entry.getValue() / totalExceptions * 100
                    ));
            stats.put("percentages", percentages);
        }
        
        // 添加最近的异常记录
        synchronized (recentExceptions) {
            stats.put("recentExceptions", recentExceptions);
        }
        
        return stats;
    }

    /**
     * 记录异常
     */
    public void recordException(String exceptionType, String message, String stackTrace) {
        // 更新指标服务中的计数
        metricsService.recordException(exceptionType);
        
        // 记录详细异常信息
        ExceptionRecord record = new ExceptionRecord(
                exceptionType,
                message,
                stackTrace,
                System.currentTimeMillis()
        );
        
        synchronized (recentExceptions) {
            recentExceptions.add(0, record); // 添加到列表开头
            
            // 保持列表大小不超过上限
            if (recentExceptions.size() > MAX_RECENT_EXCEPTIONS) {
                recentExceptions.remove(recentExceptions.size() - 1);
            }
        }
    }

    /**
     * 异常记录类
     */
    public static class ExceptionRecord {
        private final String type;
        private final String message;
        private final String stackTrace;
        private final long timestamp;
        private final String formattedTime;

        public ExceptionRecord(String type, String message, String stackTrace, long timestamp) {
            this.type = type;
            this.message = message;
            // 截断堆栈跟踪以避免过大
            this.stackTrace = stackTrace != null && stackTrace.length() > 500 
                    ? stackTrace.substring(0, 500) + "..." 
                    : stackTrace;
            this.timestamp = timestamp;
            this.formattedTime = new java.util.Date(timestamp).toString();
        }

        public String getType() {
            return type;
        }

        public String getMessage() {
            return message;
        }

        public String getStackTrace() {
            return stackTrace;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getFormattedTime() {
            return formattedTime;
        }
    }
} 