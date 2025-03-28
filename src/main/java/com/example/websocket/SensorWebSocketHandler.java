package com.example.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket处理器，用于管理WebSocket连接和推送传感器数据
 */
@Component
public class SensorWebSocketHandler extends TextWebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(SensorWebSocketHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 保存所有活跃的WebSocket会话
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    
    // 统计信息
    private final AtomicInteger totalConnections = new AtomicInteger(0);
    private final AtomicInteger messagesSent = new AtomicInteger(0);
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        
        totalConnections.incrementAndGet();
        
        logger.info("新的WebSocket连接已建立: {} (IP: {}, 当前活跃连接数: {})", 
                sessionId, getClientIp(session), sessions.size());
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        
        logger.info("WebSocket连接已关闭: {} (状态: {}, 剩余活跃连接数: {})", 
                sessionId, status, sessions.size());
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        logger.debug("接收到来自会话 {} 的消息: {}", session.getId(), payload);
        
        // 这里可以处理客户端发送的消息，例如配置请求等
        // 在此示例中，我们不需要处理客户端消息
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("WebSocket传输错误 (会话ID: {}): {}", session.getId(), exception.getMessage(), exception);
        
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }
    
    /**
     * 向所有连接的客户端广播传感器数据
     * 
     * @param sensorData 传感器数据对象
     */
    public void broadcastSensorData(SensorData sensorData) {
        if (sessions.isEmpty()) {
            return; // 没有活跃连接，不广播
        }
        
        try {
            String jsonData = objectMapper.writeValueAsString(sensorData);
            TextMessage message = new TextMessage(jsonData);
            
            int successCount = 0;
            for (WebSocketSession session : sessions.values()) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(message);
                        successCount++;
                    } catch (IOException e) {
                        logger.warn("向会话 {} 发送消息失败: {}", session.getId(), e.getMessage());
                        // 不要在循环中关闭会话，可能导致ConcurrentModificationException
                    }
                }
            }
            
            if (successCount > 0) {
                messagesSent.addAndGet(successCount);
                logger.debug("成功将传感器数据广播给 {} 个客户端", successCount);
            }
        } catch (Exception e) {
            logger.error("序列化传感器数据或广播失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 获取客户端IP地址
     */
    private String getClientIp(WebSocketSession session) {
        String ip = "未知";
        try {
            Map<String, Object> attributes = session.getAttributes();
            if (attributes.containsKey("clientIp")) {
                ip = (String) attributes.get("clientIp");
            } else {
                ip = session.getRemoteAddress().getAddress().getHostAddress();
            }
        } catch (Exception e) {
            logger.warn("无法获取客户端IP地址: {}", e.getMessage());
        }
        return ip;
    }
    
    /**
     * 获取连接统计信息
     */
    public Map<String, Object> getConnectionStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("activeConnections", sessions.size());
        stats.put("totalConnections", totalConnections.get());
        stats.put("messagesSent", messagesSent.get());
        
        // 收集会话信息
        Map<String, Map<String, Object>> sessionInfo = new ConcurrentHashMap<>();
        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            WebSocketSession session = entry.getValue();
            Map<String, Object> info = new ConcurrentHashMap<>();
            info.put("ip", getClientIp(session));
            info.put("creationTime", session.getAttributes().getOrDefault("creationTime", "未知"));
            
            sessionInfo.put(entry.getKey(), info);
        }
        
        stats.put("sessions", sessionInfo);
        return stats;
    }
} 