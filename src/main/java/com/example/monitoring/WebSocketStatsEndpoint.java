package com.example.monitoring;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket连接统计的Actuator端点
 * 提供WebSocket连接数和会话信息
 */
@Component
@Endpoint(id = "websocket-stats")
public class WebSocketStatsEndpoint {

    private final SensorMetricsService metricsService;
    private final Map<String, Map<String, Object>> sessionDetails = new ConcurrentHashMap<>();

    public WebSocketStatsEndpoint(SensorMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @ReadOperation
    public Map<String, Object> websocketStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeConnections", metricsService.getActiveConnectionsCount());
        stats.put("sessions", sessionDetails);
        return stats;
    }

    /**
     * 添加会话详情
     */
    public void addSession(String sessionId, String remoteAddress, String userAgent) {
        Map<String, Object> details = new HashMap<>();
        details.put("remoteAddress", remoteAddress);
        details.put("userAgent", userAgent);
        details.put("connectedAt", System.currentTimeMillis());
        
        sessionDetails.put(sessionId, details);
    }

    /**
     * 更新会话的最后活动时间
     */
    public void updateSessionActivity(String sessionId) {
        if (sessionDetails.containsKey(sessionId)) {
            sessionDetails.get(sessionId).put("lastActivity", System.currentTimeMillis());
        }
    }

    /**
     * 移除会话详情
     */
    public void removeSession(String sessionId) {
        sessionDetails.remove(sessionId);
    }
} 