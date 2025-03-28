package com.example;

import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.CompletableFuture;

/**
 * Flink配置类
 * 集成Spring Boot和Flink流处理
 */
@Configuration
@EnableAsync
public class FlinkConfig {
    private static final Logger LOG = LoggerFactory.getLogger(FlinkConfig.class);
    
    @Value("${sensor.device.count:3}")
    private int numberOfDevices;
    
    @Value("${sensor.http.endpoint:http://your-api.com/sensor/push}")
    private String httpEndpoint;
    
    @Value("${sensor.http.retries:3}")
    private int maxRetries;
    
    @Value("${sensor.http.timeout:5000}")
    private int httpTimeout;
    
    @Autowired
    private WebSocketSensorService webSocketService;
    
    private StreamExecutionEnvironment env;
    private CompletableFuture<Void> flinkFuture;
    
    /**
     * 初始化并启动Flink环境
     */
    @PostConstruct
    public void init() {
        LOG.info("初始化Flink环境...");
        startFlinkEnvironment();
    }
    
    /**
     * 在应用关闭时停止Flink环境
     */
    @PreDestroy
    public void cleanup() {
        LOG.info("关闭Flink环境...");
        if (env != null) {
            env.close();
        }
    }
    
    /**
     * 配置并启动Flink流处理环境
     */
    private void startFlinkEnvironment() {
        env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        // 配置传感器数据源
        DataStream<SensorData> sensorStream = env.addSource(new SensorSource(numberOfDevices))
                .name("sensor-source")
                .uid("sensor-source");
        
        // 配置HTTP发送器
        DataStream<SensorData> httpSenderStream = sensorStream
                .map(new HttpSender(httpEndpoint, maxRetries, httpTimeout))
                .name("http-sender")
                .uid("http-sender");
        
        // 添加WebSocket发送接收器
        httpSenderStream.addSink(new SinkFunction<SensorData>() {
            @Override
            public void invoke(SensorData value, Context context) {
                webSocketService.sendSensorData(value);
            }
        }).name("websocket-sink").uid("websocket-sink");
        
        // 异步执行Flink作业
        flinkFuture = CompletableFuture.runAsync(() -> {
            try {
                LOG.info("启动Flink传感器数据模拟器...");
                env.execute("Sensor Data Simulator with WebSocket");
            } catch (Exception e) {
                LOG.error("Flink作业执行失败: {}", e.getMessage(), e);
            }
        });
    }
    
    @Bean
    public StreamExecutionEnvironment streamExecutionEnvironment() {
        return env;
    }
} 