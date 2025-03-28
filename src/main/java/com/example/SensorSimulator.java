package com.example;

import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.PrintSinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 传感器模拟器主应用程序
 * 生成传感器数据并发送到HTTP端点
 */
public class SensorSimulator {
    private static final Logger LOG = LoggerFactory.getLogger(SensorSimulator.class);
    
    // 配置参数
    private static final int NUMBER_OF_DEVICES = 3; // 设备数量
    private static final int MAX_RETRIES = 3; // 最大重试次数
    private static final int HTTP_TIMEOUT = 5000; // HTTP超时时间（毫秒）
    private static final String HTTP_ENDPOINT = "http://your-api.com/sensor/push"; // HTTP端点URL
    
    public static void main(String[] args) throws Exception {
        // 解析命令行参数，如果有的话
        String endpoint = args.length > 0 ? args[0] : HTTP_ENDPOINT;
        LOG.info("使用HTTP端点: {}", endpoint);
        
        // 创建Flink流处理环境
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        // 添加传感器数据源
        DataStream<SensorData> sensorStream = env.addSource(new SensorSource(NUMBER_OF_DEVICES))
                .name("sensor-source")
                .uid("sensor-source");
        
        // 将传感器数据发送到HTTP端点
        DataStream<SensorData> httpSenderStream = sensorStream
                .map(new HttpSender(endpoint, MAX_RETRIES, HTTP_TIMEOUT))
                .name("http-sender")
                .uid("http-sender");
        
        // 添加一个打印接收器，用于调试
        httpSenderStream.addSink(new PrintSinkFunction<>())
                .name("print-sink")
                .uid("print-sink");
        
        LOG.info("开始执行传感器模拟器...");
        env.execute("Sensor Data Simulator");
    }
} 