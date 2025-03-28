package com.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * HTTP发送器类，负责将传感器数据推送到HTTP端点
 */
public class HttpSender extends RichMapFunction<SensorData, SensorData> {
    private static final Logger LOG = LoggerFactory.getLogger(HttpSender.class);
    private static final long serialVersionUID = 1L;
    
    private final String endpoint;
    private final int maxRetries;
    private final int timeout; // 毫秒
    
    private transient CloseableHttpClient httpClient;
    private transient ObjectMapper objectMapper;
    
    public HttpSender(String endpoint, int maxRetries, int timeout) {
        this.endpoint = endpoint;
        this.maxRetries = maxRetries;
        this.timeout = timeout;
    }
    
    @Override
    public void open(Configuration parameters) {
        // 配置HTTP客户端，设置连接超时和请求超时
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(timeout)
                .setSocketTimeout(timeout)
                .build();
                
        httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();
                
        objectMapper = new ObjectMapper();
    }
    
    @Override
    public SensorData map(SensorData sensorData) throws Exception {
        int attempts = 0;
        boolean success = false;
        Exception lastException = null;
        
        // 实现重试逻辑
        while (attempts < maxRetries && !success) {
            attempts++;
            try {
                sendData(sensorData);
                success = true;
            } catch (Exception e) {
                lastException = e;
                LOG.warn("发送失败，尝试 {}/{}，错误: {}", attempts, maxRetries, e.getMessage());
                
                if (attempts < maxRetries) {
                    // 指数退避策略，每次重试等待时间增加
                    Thread.sleep(1000 * attempts);
                }
            }
        }
        
        if (!success && lastException != null) {
            LOG.error("达到最大重试次数，数据发送失败: {}", sensorData);
            throw new IOException("发送传感器数据失败，已重试 " + maxRetries + " 次", lastException);
        }
        
        return sensorData; // 返回原始数据，允许下游处理
    }
    
    private void sendData(SensorData sensorData) throws IOException {
        HttpPost httpPost = new HttpPost(endpoint);
        httpPost.setHeader("Content-Type", "application/json");
        
        String jsonData = objectMapper.writeValueAsString(sensorData);
        httpPost.setEntity(new StringEntity(jsonData));
        
        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode >= 200 && statusCode < 300) {
                LOG.info("成功发送传感器数据: {}, 响应: {}", sensorData.deviceId(), statusCode);
            } else {
                throw new IOException("HTTP 错误: " + statusCode);
            }
        }
    }
    
    @Override
    public void close() throws Exception {
        if (httpClient != null) {
            httpClient.close();
        }
    }
} 