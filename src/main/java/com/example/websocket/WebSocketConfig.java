package com.example.websocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * WebSocket配置类
 * - 启用WebSocket支持
 * - 注册处理器到路径"/wx-socket"
 * - 允许跨域访问
 * - 配置消息缓冲区大小为512KB
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final SensorWebSocketHandler sensorWebSocketHandler;

    public WebSocketConfig(SensorWebSocketHandler sensorWebSocketHandler) {
        this.sensorWebSocketHandler = sensorWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(sensorWebSocketHandler, "/wx-socket")
                .addInterceptors(clientInfoInterceptor())
                .setAllowedOrigins("*"); // 允许所有来源的WebSocket连接，生产环境应限制
    }

    /**
     * 创建拦截器，用于在握手阶段获取客户端信息
     */
    private HandshakeInterceptor clientInfoInterceptor() {
        return new HttpSessionHandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(org.springframework.http.server.ServerHttpRequest request,
                                         org.springframework.http.server.ServerHttpResponse response,
                                         org.springframework.web.socket.WebSocketHandler wsHandler,
                                         Map<String, Object> attributes) throws Exception {
                
                // 获取客户端IP地址
                if (request.getRemoteAddress() != null) {
                    attributes.put("clientIp", request.getRemoteAddress().getHostString());
                }
                
                // 记录连接时间
                attributes.put("creationTime", 
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                
                // 如果请求来自HttpServletRequest，则获取更多信息
                if (request instanceof org.springframework.http.server.ServletServerHttpRequest) {
                    HttpServletRequest servletRequest = 
                        ((org.springframework.http.server.ServletServerHttpRequest) request).getServletRequest();
                    
                    // 获取客户端信息如用户代理
                    attributes.put("userAgent", servletRequest.getHeader("User-Agent"));
                    
                    // 获取来源页面
                    String referer = servletRequest.getHeader("Referer");
                    if (referer != null) {
                        attributes.put("referer", referer);
                    }
                }
                
                return super.beforeHandshake(request, response, wsHandler, attributes);
            }
        };
    }

    /**
     * 配置WebSocket容器
     * - 设置消息缓冲区大小为512KB
     * - 设置会话空闲超时
     * - 设置异步发送超时
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        
        // 设置消息缓冲区大小为512KB (512 * 1024)
        container.setMaxTextMessageBufferSize(512 * 1024);
        container.setMaxBinaryMessageBufferSize(512 * 1024);
        
        // 设置会话空闲超时为60秒
        container.setMaxSessionIdleTimeout(60000L);
        
        // 设置异步发送超时为10秒
        container.setAsyncSendTimeout(10000L);
        
        return container;
    }
} 