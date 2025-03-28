package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Flink传感器数据模拟器应用程序主类
 */
@SpringBootApplication
@EnableScheduling
public class FlinkSensorSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlinkSensorSimulatorApplication.class, args);
    }
} 