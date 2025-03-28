package com.example.websocket;

import java.time.Instant;

/**
 * 传感器数据模型类，表示从传感器接收到的数据点
 */
public record SensorData(
    String deviceId,      // 设备ID
    double value,         // 传感器值
    String unit,          // 单位
    String type,          // 传感器类型 (温度、湿度、压力等)
    long timestamp        // 时间戳 (毫秒)
) {
    /**
     * 创建带有当前时间戳的传感器数据
     */
    public static SensorData create(String deviceId, double value, String unit, String type) {
        return new SensorData(deviceId, value, unit, type, System.currentTimeMillis());
    }
    
    /**
     * 格式化为人类可读的字符串
     */
    @Override
    public String toString() {
        return String.format("SensorData{deviceId='%s', value=%.2f %s, type='%s', timestamp=%s}",
                deviceId, value, unit, type, Instant.ofEpochMilli(timestamp));
    }
} 