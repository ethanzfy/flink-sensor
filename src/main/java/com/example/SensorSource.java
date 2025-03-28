package com.example;

import org.apache.flink.streaming.api.functions.source.RichSourceFunction;
import org.apache.flink.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * 传感器数据源，生成模拟传感器数据
 */
public class SensorSource extends RichSourceFunction<SensorData> {
    private static final Logger LOG = LoggerFactory.getLogger(SensorSource.class);
    private static final long serialVersionUID = 1L;
    
    private final int numberOfDevices;
    private volatile boolean isRunning = true;
    private transient Random random;
    
    public SensorSource(int numberOfDevices) {
        this.numberOfDevices = numberOfDevices;
    }
    
    @Override
    public void open(Configuration parameters) {
        random = new Random();
    }
    
    @Override
    public void run(SourceContext<SensorData> ctx) throws Exception {
        while (isRunning) {
            // 为每个设备生成一条数据
            for (int i = 1; i <= numberOfDevices; i++) {
                String deviceId = "device_" + i;
                long timestamp = System.currentTimeMillis();
                // 生成一个正态分布的值，均值为20，标准差为5
                // 确保值始终为正数
                double value = Math.max(0.1, 20 + random.nextGaussian() * 5);
                
                // 使用新的Record构造方式创建SensorData
                SensorData data = new SensorData(deviceId, timestamp, value);
                ctx.collect(data);
                
                LOG.debug("生成传感器数据: {}", data);
            }
            
            // 每秒生成一次数据
            Thread.sleep(1000);
        }
    }
    
    @Override
    public void cancel() {
        isRunning = false;
    }
} 