package com.example.websocket;

import com.example.SensorApplication;
import com.example.SensorData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 传感器数据WebSocket测试类
 * 测试WebSocket连接、数据格式、延迟和性能
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = SensorApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SensorWebSocketTest {

    private static final Logger LOG = LoggerFactory.getLogger(SensorWebSocketTest.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int TIMEOUT_SECONDS = 30;
    private static final int CONCURRENT_CLIENTS = 10;
    private static final int CONTINUOUS_DATA_COUNT = 100;
    private static final int MIN_MESSAGES_PER_CLIENT = 5; // 每个客户端至少应接收的消息数
    
    // 用于验证ISO-8601格式的时间戳
    private static final Pattern ISO8601_PATTERN = 
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})$");

    @LocalServerPort
    private int port;

    private final List<TestWebSocketClient> testClients = new ArrayList<>();
    private final ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_CLIENTS);

    @BeforeEach
    public void setup() {
        // 清空客户端列表
        testClients.clear();
    }

    @AfterEach
    public void cleanup() throws Exception {
        // 关闭所有WebSocket客户端连接
        for (TestWebSocketClient client : testClients) {
            if (client.isOpen()) {
                client.closeBlocking();
            }
        }
        
        // 关闭线程池
        executorService.shutdown();
        if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
            executorService.shutdownNow();
        }
    }

    /**
     * 测试多个并发WebSocket连接
     * - 创建10个并发连接并验证它们是否都能成功连接
     * - 验证每个客户端是否都能接收到消息
     */
    @Test
    @DisplayName("测试10个并发WebSocket连接")
    public void testConcurrentConnections() throws Exception {
        LOG.info("开始测试10个并发WebSocket连接");
        CountDownLatch connectionLatch = new CountDownLatch(CONCURRENT_CLIENTS);
        CountDownLatch messageLatch = new CountDownLatch(CONCURRENT_CLIENTS * MIN_MESSAGES_PER_CLIENT);
        
        // 创建并启动10个WebSocket客户端
        for (int i = 0; i < CONCURRENT_CLIENTS; i++) {
            final int clientId = i;
            executorService.submit(() -> {
                try {
                    TestWebSocketClient client = createAndConnectClient(clientId, connectionLatch, messageLatch);
                    synchronized (testClients) {
                        testClients.add(client);
                    }
                } catch (Exception e) {
                    LOG.error("客户端 {} 连接失败", clientId, e);
                    connectionLatch.countDown(); // 确保latch会被释放
                }
            });
        }
        
        // 等待所有客户端连接
        boolean allConnected = connectionLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        
        // 验证所有客户端是否都已连接
        assertTrue(allConnected, "所有WebSocket客户端应该能够成功连接");
        assertEquals(CONCURRENT_CLIENTS, testClients.size(), "应该有10个WebSocket客户端创建");
        
        // 确认所有客户端连接状态
        for (TestWebSocketClient client : testClients) {
            assertTrue(client.isOpen(), "客户端 " + client.getClientId() + " 应该处于连接状态");
        }
        
        // 等待每个客户端接收至少5条消息
        boolean allReceivedMessages = messageLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue(allReceivedMessages, "所有客户端应该接收到至少" + MIN_MESSAGES_PER_CLIENT + "条消息");
        
        // 确认每个客户端都接收到了消息
        for (TestWebSocketClient client : testClients) {
            assertTrue(client.getReceivedMessages().size() >= MIN_MESSAGES_PER_CLIENT, 
                    "客户端 " + client.getClientId() + " 应该接收到至少" + MIN_MESSAGES_PER_CLIENT + "条消息");
        }
        
        LOG.info("10个并发WebSocket连接测试完成");
    }

    /**
     * 测试传感器数据格式
     * - 验证时间戳格式是否符合ISO-8601标准
     * - 验证数据字段是否符合预期
     */
    @Test
    @DisplayName("测试传感器数据格式和时间戳")
    public void testDataFormatAndTimestamp() throws Exception {
        LOG.info("开始测试传感器数据格式和时间戳");
        CountDownLatch connectionLatch = new CountDownLatch(1);
        CountDownLatch messageLatch = new CountDownLatch(MIN_MESSAGES_PER_CLIENT);
        
        // 创建单个WebSocket客户端
        TestWebSocketClient client = createAndConnectClient(0, connectionLatch, messageLatch);
        testClients.add(client);
        
        // 等待连接和接收消息
        assertTrue(connectionLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "WebSocket客户端应该成功连接");
        assertTrue(messageLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "WebSocket客户端应该接收到消息");
        
        // 获取接收到的消息
        List<String> messages = client.getReceivedMessages();
        assertFalse(messages.isEmpty(), "应该接收到传感器数据消息");
        
        // 验证所有消息的格式和时间戳
        for (String message : messages) {
            // 解析消息为JSON
            SensorData sensorData = OBJECT_MAPPER.readValue(message, SensorData.class);
            
            // 验证设备ID格式
            assertTrue(sensorData.deviceId().startsWith("device_"), "设备ID应该以'device_'开头");
            
            // 验证时间戳格式
            String formattedTimestamp = sensorData.getFormattedTimestamp();
            assertTrue(ISO8601_PATTERN.matcher(formattedTimestamp).matches(), 
                    "时间戳应该符合ISO-8601格式: " + formattedTimestamp);
            
            // 尝试解析时间戳
            try {
                Instant.parse(formattedTimestamp);
            } catch (DateTimeParseException e) {
                fail("时间戳无法被解析为有效的ISO-8601格式: " + formattedTimestamp);
            }
            
            // 验证数值范围
            assertTrue(sensorData.value() > 0, "传感器数值应该大于0");
        }
        
        LOG.info("传感器数据格式和时间戳测试完成");
    }

    /**
     * 测试数据延迟
     * - 测量从服务器发送数据到客户端接收的延迟
     */
    @Test
    @DisplayName("测试数据传输延迟")
    public void testDataLatency() throws Exception {
        LOG.info("开始测试数据传输延迟");
        CountDownLatch connectionLatch = new CountDownLatch(1);
        CountDownLatch messageLatch = new CountDownLatch(20); // 收集20条消息进行延迟测试
        
        // 创建带延迟测量的WebSocket客户端
        LatencyMeasuringClient client = new LatencyMeasuringClient(
                new URI("ws://localhost:" + port + "/wx-socket"), 0, connectionLatch, messageLatch);
        client.connect();
        testClients.add(client);
        
        // 等待连接和接收消息
        assertTrue(connectionLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "WebSocket客户端应该成功连接");
        assertTrue(messageLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "WebSocket客户端应该接收到足够的消息");
        
        // 获取延迟统计
        Map<String, Long> latencyStats = client.getLatencyStats();
        assertFalse(latencyStats.isEmpty(), "应该收集到延迟统计信息");
        
        // 计算平均延迟、最小延迟和最大延迟
        long totalLatency = 0;
        long minLatency = Long.MAX_VALUE;
        long maxLatency = 0;
        
        for (Long latency : latencyStats.values()) {
            totalLatency += latency;
            minLatency = Math.min(minLatency, latency);
            maxLatency = Math.max(maxLatency, latency);
        }
        
        double avgLatency = (double) totalLatency / latencyStats.size();
        
        LOG.info("延迟统计: 平均={} ms, 最小={} ms, 最大={} ms", 
                String.format("%.2f", avgLatency), minLatency, maxLatency);
        
        // 验证延迟合理性
        assertTrue(avgLatency < 5000, "平均延迟应该小于5秒");
        
        LOG.info("数据传输延迟测试完成");
    }

    /**
     * 压力测试和内存泄露测试
     * - 持续接收100条消息，监控内存使用情况
     */
    @Test
    @DisplayName("压力测试和内存泄露检测")
    public void testStressAndMemoryLeak() throws Exception {
        LOG.info("开始压力测试和内存泄露检测");
        CountDownLatch connectionLatch = new CountDownLatch(1);
        CountDownLatch messageLatch = new CountDownLatch(CONTINUOUS_DATA_COUNT);
        
        // 记录测试开始时的内存使用情况
        long initialMemory = getUsedMemory();
        LOG.info("初始内存使用: {} MB", initialMemory / (1024 * 1024));
        
        // 创建WebSocket客户端
        TestWebSocketClient client = createAndConnectClient(0, connectionLatch, messageLatch);
        testClients.add(client);
        
        // 等待连接
        assertTrue(connectionLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "WebSocket客户端应该成功连接");
        
        // 等待接收100条消息
        assertTrue(messageLatch.await(TIMEOUT_SECONDS * 3, TimeUnit.SECONDS), 
                "WebSocket客户端应该接收到" + CONTINUOUS_DATA_COUNT + "条消息");
        
        // 验证接收的消息数
        assertEquals(CONTINUOUS_DATA_COUNT, client.getReceivedMessages().size(), 
                "客户端应该接收到" + CONTINUOUS_DATA_COUNT + "条消息");
        
        // 观察上次GC后的内存使用情况
        System.gc();
        Thread.sleep(1000);
        long finalMemory = getUsedMemory();
        LOG.info("最终内存使用: {} MB", finalMemory / (1024 * 1024));
        
        // 计算内存差异
        long memoryDifference = finalMemory - initialMemory;
        LOG.info("内存变化: {} MB", memoryDifference / (1024 * 1024));
        
        // 内存增长不应过大
        // 注意: 这不是绝对的内存泄露检测，但可作为参考
        assertTrue(memoryDifference < 50 * 1024 * 1024, "内存增长不应超过50MB，可能存在内存泄露");
        
        LOG.info("压力测试和内存泄露检测完成");
    }

    /**
     * 创建并连接一个WebSocket测试客户端
     */
    private TestWebSocketClient createAndConnectClient(int clientId, 
            CountDownLatch connectionLatch, CountDownLatch messageLatch) throws Exception {
        URI serverUri = new URI("ws://localhost:" + port + "/wx-socket");
        TestWebSocketClient client = new TestWebSocketClient(serverUri, clientId, connectionLatch, messageLatch);
        client.connect();
        return client;
    }
    
    /**
     * 获取当前使用的内存
     */
    private long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    /**
     * 测试用WebSocket客户端
     */
    private static class TestWebSocketClient extends WebSocketClient {
        private final int clientId;
        private final CountDownLatch connectionLatch;
        private final CountDownLatch messageLatch;
        private final List<String> receivedMessages = Collections.synchronizedList(new ArrayList<>());

        public TestWebSocketClient(URI serverUri, int clientId, 
                CountDownLatch connectionLatch, CountDownLatch messageLatch) {
            super(serverUri);
            this.clientId = clientId;
            this.connectionLatch = connectionLatch;
            this.messageLatch = messageLatch;
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            LOG.info("客户端 {} 连接成功", clientId);
            connectionLatch.countDown();
        }

        @Override
        public void onMessage(String message) {
            synchronized (receivedMessages) {
                receivedMessages.add(message);
            }
            
            // 只减少计数直到初始数量
            if (messageLatch.getCount() > 0) {
                messageLatch.countDown();
            }
            
            // 心跳消息不计入统计
            if (!"ACK".equals(message)) {
                try {
                    SensorData data = OBJECT_MAPPER.readValue(message, SensorData.class);
                    LOG.debug("客户端 {} 收到消息: {}, {}, {}", 
                            clientId, data.deviceId(), data.timestamp(), data.value());
                } catch (Exception e) {
                    LOG.error("解析消息时出错", e);
                }
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            LOG.info("客户端 {} 连接关闭: code={}, reason={}, remote={}", clientId, code, reason, remote);
        }

        @Override
        public void onError(Exception ex) {
            LOG.error("客户端 {} 发生错误", clientId, ex);
        }

        public int getClientId() {
            return clientId;
        }

        public List<String> getReceivedMessages() {
            return receivedMessages;
        }
    }

    /**
     * 具有延迟测量功能的WebSocket客户端
     */
    private static class LatencyMeasuringClient extends TestWebSocketClient {
        private final Map<String, Long> latencyStats = new ConcurrentHashMap<>();

        public LatencyMeasuringClient(URI serverUri, int clientId, 
                CountDownLatch connectionLatch, CountDownLatch messageLatch) {
            super(serverUri, clientId, connectionLatch, messageLatch);
        }

        @Override
        public void onMessage(String message) {
            long receiveTime = System.currentTimeMillis();
            
            // 调用父类方法处理消息
            super.onMessage(message);
            
            // 心跳消息不计入延迟统计
            if (!"ACK".equals(message)) {
                try {
                    SensorData data = OBJECT_MAPPER.readValue(message, SensorData.class);
                    long sendTime = data.timestamp();
                    long latency = receiveTime - sendTime;
                    
                    // 存储延迟数据
                    latencyStats.put(data.deviceId() + ":" + sendTime, latency);
                    
                    LOG.debug("消息延迟: {} ms, 设备: {}", latency, data.deviceId());
                } catch (Exception e) {
                    LOG.error("计算延迟时出错", e);
                }
            }
        }

        public Map<String, Long> getLatencyStats() {
            return latencyStats;
        }
    }
} 