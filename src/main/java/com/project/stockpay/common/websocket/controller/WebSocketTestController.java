package com.project.stockpay.common.websocket.controller;

import com.project.stockpay.common.websocket.KisWebSocketClient;
import com.project.stockpay.common.websocket.dto.RealTimeOrderbookDto;
import com.project.stockpay.common.websocket.dto.RealTimeStockPriceDto;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/test/websocket")
@RequiredArgsConstructor
@Slf4j
public class WebSocketTestController {

    private final KisWebSocketClient webSocketClient;
    private final RedisTemplate<String, Object> redisTemplate;

    // 연결 테스트
    @GetMapping("/connect")
    public ResponseEntity<Map<String, Object>> testConnection() {
        try {
            log.info("웹소켓 연결 테스트 시작");

            Map<String, Object> result = new HashMap<>();
            result.put("timestamp", LocalDateTime.now().toString());

            // 연결 상태 확인
            boolean connected = webSocketClient.isConnected();
            result.put("connected", connected);

            if (!connected) {
                // 연결 시도
                webSocketClient.connect();

                // 5초 대기 후 재확인
                Thread.sleep(5000);
                connected = webSocketClient.isConnected();
                result.put("connected_after_retry", connected);
            }

            result.put("status", connected ? "SUCCESS" : "FAILED");
            result.put("message", connected ? "웹소켓 연결 성공" : "웹소켓 연결 실패");

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("웹소켓 연결 테스트 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    // 삼성전자 실시간 데이터 테스트
    @GetMapping("/samsung")
    public ResponseEntity<Map<String, Object>> testSamsungStock() {
        try {
            String stockCode = "005930"; // 삼성전자

            // 실시간 구독
            webSocketClient.subscribeStockPrice(stockCode);
            webSocketClient.subscribeStockOrderbook(stockCode);

            // 3초 대기
            Thread.sleep(3000);

            // Redis에서 데이터 조회
            RealTimeStockPriceDto priceData = (RealTimeStockPriceDto)
                    redisTemplate.opsForValue().get("realtime:stock:" + stockCode);

            RealTimeOrderbookDto orderbookData = (RealTimeOrderbookDto)
                    redisTemplate.opsForValue().get("realtime:orderbook:" + stockCode);

            Map<String, Object> result = new HashMap<>();
            result.put("stockCode", stockCode);
            result.put("stockName", "삼성전자");
            result.put("priceData", priceData);
            result.put("orderbookData", orderbookData);
            result.put("timestamp", LocalDateTime.now().toString());

            if (priceData != null || orderbookData != null) {
                result.put("status", "SUCCESS");
                result.put("message", "실시간 데이터 수신 중");
            } else {
                result.put("status", "NO_DATA");
                result.put("message", "실시간 데이터 미수신 (장시간 확인 필요)");
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("삼성전자 실시간 데이터 테스트 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    // 다중 종목 구독 테스트
    @PostMapping("/subscribe-multiple")
    public ResponseEntity<Map<String, Object>> subscribeMultipleStocks(
            @RequestBody List<String> stockCodes) {

        try {
            Map<String, Object> result = new HashMap<>();
            List<String> subscribed = new ArrayList<>();
            List<String> failed = new ArrayList<>();

            for (String stockCode : stockCodes) {
                try {
                    webSocketClient.subscribeStockPrice(stockCode);
                    subscribed.add(stockCode);
                    Thread.sleep(100); // 0.1초 간격
                } catch (Exception e) {
                    failed.add(stockCode);
                    log.error("종목 구독 실패: {}", stockCode, e);
                }
            }

            result.put("total", stockCodes.size());
            result.put("subscribed", subscribed);
            result.put("failed", failed);
            result.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("다중 종목 구독 테스트 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    // Redis 캐시 상태 확인
    @GetMapping("/cache-status")
    public ResponseEntity<Map<String, Object>> getCacheStatus() {
        try {
            Set<String> stockKeys = redisTemplate.keys("realtime:stock:*");
            Set<String> orderbookKeys = redisTemplate.keys("realtime:orderbook:*");

            Map<String, Object> result = new HashMap<>();
            result.put("stock_cache_count", stockKeys != null ? stockKeys.size() : 0);
            result.put("orderbook_cache_count", orderbookKeys != null ? orderbookKeys.size() : 0);

            // 최근 데이터 샘플
            if (stockKeys != null && !stockKeys.isEmpty()) {
                String sampleKey = stockKeys.iterator().next();
                Object sampleData = redisTemplate.opsForValue().get(sampleKey);
                result.put("sample_stock_data", sampleData);
            }

            result.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("캐시 상태 확인 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    // 승인키 테스트
    @GetMapping("/approval-key")
    public ResponseEntity<Map<String, Object>> testApprovalKey() {
        try {
            String approvalKey = webSocketClient.getApprovalKey();

            Map<String, Object> result = new HashMap<>();
            result.put("approval_key", approvalKey != null ? "발급됨" : "발급실패");
            result.put("key_length", approvalKey != null ? approvalKey.length() : 0);
            result.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("승인키 테스트 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    // 웹소켓 메시지 통계
    @GetMapping("/message-stats")
    public ResponseEntity<Map<String, Object>> getMessageStats() {
        try {
            // Redis에서 통계 데이터 조회
            String statsKey = "websocket:stats";
            Map<Object, Object> stats = redisTemplate.opsForHash().entries(statsKey);

            Map<String, Object> result = new HashMap<>();
            result.put("total_messages", stats.getOrDefault("total_messages", 0));
            result.put("price_messages", stats.getOrDefault("price_messages", 0));
            result.put("orderbook_messages", stats.getOrDefault("orderbook_messages", 0));
            result.put("error_count", stats.getOrDefault("error_count", 0));
            result.put("last_message_time", stats.getOrDefault("last_message_time", "없음"));
            result.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("메시지 통계 조회 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }
}