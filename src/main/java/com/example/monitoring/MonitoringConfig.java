package com.example.monitoring;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 监控配置类
 * 配置Actuator和Micrometer
 */
@Configuration
@EnableScheduling  // 启用定时任务
@EnableAspectJAutoProxy  // 启用AOP
public class MonitoringConfig {

    /**
     * 自定义MeterRegistry，添加通用标签
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> {
            registry.config().commonTags(
                    "application", "flink-sensor-simulator",
                    "host", getHostname()
            );
        };
    }

    /**
     * 创建TimedAspect，支持@Timed注解
     */
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    /**
     * 获取主机名作为标签
     */
    private String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }
} 