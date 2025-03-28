package com.example.controller;

import com.example.simulator.SensorDataSimulator;
import com.example.websocket.SensorWebSocketHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 传感器模拟器控制器，提供REST API端点用于控制模拟器和查看状态
 */
@RestController
@RequestMapping("/api/simulator")
public class SimulatorController {

    private final SensorDataSimulator simulator;
    private final SensorWebSocketHandler webSocketHandler;

    public SimulatorController(SensorDataSimulator simulator, SensorWebSocketHandler webSocketHandler) {
        this.simulator = simulator;
        this.webSocketHandler = webSocketHandler;
    }

    /**
     * 获取模拟器状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("simulator", simulator.getStatus());
        status.put("websocket", webSocketHandler.getConnectionStats());
        return ResponseEntity.ok(status);
    }

    /**
     * 重置模拟器
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> reset() {
        simulator.reset();
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "模拟器已重置");
        response.put("status", simulator.getStatus());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 健康检查端点
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("simulator", "运行中");
        health.put("websocket", "可用");
        health.put("timestamp", String.valueOf(System.currentTimeMillis()));
        
        return ResponseEntity.ok(health);
    }
} 