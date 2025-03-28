package com.example;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * 传感器数据模型类（使用Java Record）
 * 包含设备ID、时间戳和数值
 */
public record SensorData(
        @JsonProperty("deviceId")
        @NotBlank(message = "设备ID不能为空")
        String deviceId,

        @JsonProperty("timestamp")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
        @Min(value = 0, message = "时间戳必须是正数")
        long timestamp,

        @JsonProperty("value")
        @Min(value = 0, message = "数值必须是正数")
        double value
) implements Serializable {

    /**
     * 显式的序列化版本UID
     */
    private static final long serialVersionUID = 1L;

    /**
     * Record的全参构造函数和toString()方法已由Java自动生成
     * 这里用于显式编译期间验证record的完整性
     */
    public SensorData {
        Objects.requireNonNull(deviceId, "设备ID不能为null");
        if (timestamp < 0) {
            throw new IllegalArgumentException("时间戳必须是正数");
        }
    }

    /**
     * 创建一个带有当前时间戳的SensorData实例
     */
    public static SensorData now(String deviceId, double value) {
        return new SensorData(deviceId, System.currentTimeMillis(), value);
    }

    /**
     * 获取格式化的ISO-8601时间字符串
     */
    public String getFormattedTimestamp() {
        return Instant.ofEpochMilli(timestamp).toString();
    }

    /**
     * 重写toString方法，提供更友好的输出格式
     */
    @Override
    public String toString() {
        return "SensorData{" +
                "deviceId='" + deviceId + '\'' +
                ", timestamp=" + timestamp + " (" + getFormattedTimestamp() + ")" +
                ", value=" + value +
                '}';
    }
} 