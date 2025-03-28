package com.example.simulator;

import com.example.websocket.SensorData;
import com.example.websocket.SensorWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 传感器数据模拟器，用于生成模拟传感器数据并通过WebSocket发送
 */
@Component
@EnableScheduling
public class SensorDataSimulator {
    private static final Logger logger = LoggerFactory.getLogger(SensorDataSimulator.class);
    
    private final SensorWebSocketHandler webSocketHandler;
    private final Random random = new Random();
    
    // 设备配置
    private static final String[] DEVICE_IDS = {
            "temp-sensor-1", "temp-sensor-2", "temp-sensor-3",
            "humidity-sensor-1", "humidity-sensor-2",
            "pressure-sensor-1"
    };
    
    // 传感器类型及其配置
    private static final String[] SENSOR_TYPES = {
            "temperature", "humidity", "pressure"
    };
    
    private static final String[] UNITS = {
            "°C", "%", "hPa"
    };
    
    // 基准值
    private static final double[] BASE_VALUES = {
            22.0,  // 温度基准值 (摄氏度)
            50.0,  // 湿度基准值 (%)
            1013.0 // 气压基准值 (hPa)
    };
    
    // 变化范围
    private static final double[] VARIATION_RANGES = {
            5.0,  // 温度变化范围 (±5°C)
            20.0, // 湿度变化范围 (±20%)
            10.0  // 气压变化范围 (±10hPa)
    };
    
    // 定时任务配置
    @Value("${simulator.enabled:true}")
    private boolean simulatorEnabled;
    
    @Value("${simulator.interval:1000}")
    private long simulationInterval;
    
    // 数据生成计数器
    private final AtomicInteger dataCounter = new AtomicInteger(0);
    
    // 当前值缓存 (用于生成连续变化的数据)
    private final double[] currentValues = new double[DEVICE_IDS.length];
    
    public SensorDataSimulator(SensorWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
        
        // 初始化当前值
        for (int i = 0; i < DEVICE_IDS.length; i++) {
            String deviceId = DEVICE_IDS[i];
            int typeIndex = getSensorTypeIndex(deviceId);
            currentValues[i] = BASE_VALUES[typeIndex];
        }
        
        logger.info("传感器数据模拟器已初始化，设备数量: {}", DEVICE_IDS.length);
    }
    
    /**
     * 定时生成并发送传感器数据
     */
    @Scheduled(fixedRateString = "${simulator.interval:1000}")
    public void generateAndSendData() {
        if (!simulatorEnabled) {
            return;
        }
        
        try {
            // 选择一个随机设备生成数据
            int deviceIndex = random.nextInt(DEVICE_IDS.length);
            String deviceId = DEVICE_IDS[deviceIndex];
            
            // 确定传感器类型和配置
            int typeIndex = getSensorTypeIndex(deviceId);
            String type = SENSOR_TYPES[typeIndex];
            String unit = UNITS[typeIndex];
            
            // 生成模拟数据 (布朗运动模式: 当前值 + 随机变化)
            double baseValue = BASE_VALUES[typeIndex];
            double variationRange = VARIATION_RANGES[typeIndex];
            
            // 生成一个随机变化量 (-0.5 到 0.5 的范围)
            double change = (random.nextDouble() - 0.5) * variationRange * 0.1;
            
            // 计算新值 (限制在合理范围内)
            double currentValue = currentValues[deviceIndex];
            double newValue = currentValue + change;
            
            // 确保值在合理范围内 (基准值 ± 变化范围)
            double minValue = baseValue - variationRange;
            double maxValue = baseValue + variationRange;
            
            // 如果超出范围，则向反方向调整
            if (newValue < minValue) {
                newValue = minValue + random.nextDouble() * (variationRange * 0.1);
            } else if (newValue > maxValue) {
                newValue = maxValue - random.nextDouble() * (variationRange * 0.1);
            }
            
            // 更新当前值
            currentValues[deviceIndex] = newValue;
            
            // 创建传感器数据对象
            SensorData sensorData = SensorData.create(deviceId, newValue, unit, type);
            
            // 通过WebSocket发送数据
            webSocketHandler.broadcastSensorData(sensorData);
            
            // 计数并记录日志
            int count = dataCounter.incrementAndGet();
            if (count % 100 == 0) {
                logger.info("已生成 {} 个传感器数据点", count);
            }
        } catch (Exception e) {
            logger.error("生成传感器数据时发生错误: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 根据设备ID获取传感器类型索引
     */
    private int getSensorTypeIndex(String deviceId) {
        if (deviceId.startsWith("temp")) {
            return 0; // 温度
        } else if (deviceId.startsWith("humid")) {
            return 1; // 湿度
        } else if (deviceId.startsWith("pressure")) {
            return 2; // 气压
        }
        return 0; // 默认为温度
    }
    
    /**
     * 重置模拟器状态
     */
    public void reset() {
        for (int i = 0; i < DEVICE_IDS.length; i++) {
            int typeIndex = getSensorTypeIndex(DEVICE_IDS[i]);
            currentValues[i] = BASE_VALUES[typeIndex];
        }
        dataCounter.set(0);
        logger.info("传感器数据模拟器已重置");
    }
    
    /**
     * 获取模拟器状态
     */
    public String getStatus() {
        return String.format("传感器数据模拟器状态: 已生成 %d 个数据点, 启用状态: %s, 生成间隔: %d ms",
                dataCounter.get(), simulatorEnabled, simulationInterval);
    }
} 