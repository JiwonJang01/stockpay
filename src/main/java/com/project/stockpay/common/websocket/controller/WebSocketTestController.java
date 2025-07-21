package com.project.stockpay.common.websocket.controller;

import com.project.stockpay.common.websocket.KisWebSocketClient;
import com.project.stockpay.common.websocket.dto.MessageStats;
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
@RequestMapping("/test/websocket")
@RequiredArgsConstructor
@Slf4j
public class WebSocketTestController {

  private final KisWebSocketClient webSocketClient;
  private final RedisTemplate<String, Object> redisTemplate;

  // 연결 테스트
  @GetMapping("/status")
  public ResponseEntity<Map<String, Object>> testConnection() {
    try {
      log.info("웹소켓 연결 테스트 시작");

      // TODO: DTO로 뺄지 그냥 Map으로 둘지..?
      Map<String, Object> result = new HashMap<>();
      result.put("timestamp", LocalDateTime.now().toString());

      // 연결 상태 확인
      boolean connected = webSocketClient.isConnected();
      result.put("connected", connected);

      if (!connected) {
        result.put("message", "웹소켓이 연결되지 않았습니다. 서버 로그를 확인해주세요.");
        result.put("status", "DISCONNECTED");
        result.put("suggestion", "애플리케이션 재시작이나 KIS API 설정을 확인해주세요.");
      } else {
        result.put("message", "웹소켓 연결 상태 양호");
        result.put("status", "CONNECTED");
      }

      return ResponseEntity.ok(result);

    } catch (Exception e) {
      log.error("웹소켓 연결 테스트 실패", e);
      return ResponseEntity.internalServerError()
          .body(Map.of(
              "status", "ERROR",
              "message", e.getMessage(),
              "timestamp", LocalDateTime.now().toString()
          ));
    }
  }

  // 삼성전자 실시간 데이터 테스트
  @GetMapping("/samsung")
  public ResponseEntity<Map<String, Object>> testSamsungStock() {
    try {
      String stockCode = "005930"; // 삼성전자

      // TODO: DTO로 뺄지 그냥 Map으로 둘지..?
      Map<String, Object> result = new HashMap<>();
      result.put("stockCode", stockCode);
      result.put("stockName", "삼성전자");
      result.put("timestamp", LocalDateTime.now().toString());

      // 연결 상태 먼저 확인
      if (!webSocketClient.isConnected()) {
        result.put("status", "DISCONNECTED");
        result.put("message", "웹소켓이 연결되지 않았습니다.");
        return ResponseEntity.ok(result);
      }

      // 실시간 구독 시도
      try {
        webSocketClient.subscribeStockPrice(stockCode);
        webSocketClient.subscribeStockOrderbook(stockCode);
        result.put("subscriptionStatus", "SUCCESS");
      } catch (Exception e) {
        result.put("subscriptionStatus", "FAILED");
        result.put("subscriptionError", e.getMessage());
      }

      // 잠시 대기 (구독 후 데이터 수신 대기)
      Thread.sleep(3000);

      // Redis에서 데이터 조회
      RealTimeStockPriceDto priceData = (RealTimeStockPriceDto)
          redisTemplate.opsForValue().get("realtime:stock:" + stockCode);

      RealTimeOrderbookDto orderbookData = (RealTimeOrderbookDto)
          redisTemplate.opsForValue().get("realtime:orderbook:" + stockCode);

      result.put("priceData", priceData);
      result.put("orderbookData", orderbookData);

      // 데이터 수신 상태 판단
      if (priceData != null || orderbookData != null) {
        result.put("status", "SUCCESS");
        result.put("message", "실시간 데이터 수신 중");

        if (priceData != null) {
          result.put("priceDataAge", System.currentTimeMillis() - priceData.getTimestamp());
        }
      } else {
        result.put("status", "NO_DATA");
        result.put("message", "실시간 데이터 미수신 (장시간이거나 구독 실패)");
        result.put("troubleshooting", Arrays.asList(
            "1. 주식시장 개장시간인지 확인",
            "2. KIS API 승인키 상태 확인",
            "3. 웹소켓 연결 상태 확인"
        ));
      }

      return ResponseEntity.ok(result);

    } catch (Exception e) {
      log.error("삼성전자 실시간 데이터 테스트 실패", e);
      return ResponseEntity.internalServerError()
          .body(Map.of(
              "status", "ERROR",
              "message", e.getMessage(),
              "timestamp", LocalDateTime.now().toString()
          ));
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

      // 연결 상태 확인
      if (!webSocketClient.isConnected()) {
        result.put("status", "DISCONNECTED");
        result.put("message", "웹소켓이 연결되지 않았습니다.");
        return ResponseEntity.ok(result);
      }

      // 각 종목 구독 시도
      for (String stockCode : stockCodes) {
        try {
          webSocketClient.subscribeStockPrice(stockCode);
          subscribed.add(stockCode);
          Thread.sleep(200); // 0.2초 간격으로 증가 (API 부하 방지)
        } catch (Exception e) {
          failed.add(stockCode);
          log.error("종목 구독 실패: {}", stockCode, e);
        }
      }

      // TODO: DTO로 분리
      result.put("total", stockCodes.size());
      result.put("subscribed", subscribed);
      result.put("subscribedCount", subscribed.size());
      result.put("failed", failed);
      result.put("failedCount", failed.size());
      result.put("timestamp", LocalDateTime.now().toString());

      if (failed.isEmpty()) {
        result.put("status", "ALL_SUCCESS");
        result.put("message", "모든 종목 구독 성공");
      } else if (subscribed.isEmpty()) {
        result.put("status", "ALL_FAILED");
        result.put("message", "모든 종목 구독 실패");
      } else {
        result.put("status", "PARTIAL_SUCCESS");
        result.put("message", "일부 종목 구독 성공");
      }

      return ResponseEntity.ok(result);

    } catch (Exception e) {
      log.error("다중 종목 구독 테스트 실패", e);
      return ResponseEntity.internalServerError()
          .body(Map.of(
              "status", "ERROR",
              "message", e.getMessage(),
              "timestamp", LocalDateTime.now().toString()
          ));
    }
  }

  // Redis 캐시 상태 확인
  @GetMapping("/cache-status")
  public ResponseEntity<Map<String, Object>> getCacheStatus() {
    try {
      Set<String> stockKeys = redisTemplate.keys("realtime:stock:*");
      Set<String> orderbookKeys = redisTemplate.keys("realtime:orderbook:*");

      Map<String, Object> result = new HashMap<>();
      result.put("stockCacheCount", stockKeys != null ? stockKeys.size() : 0);
      result.put("orderbookCacheCount", orderbookKeys != null ? orderbookKeys.size() : 0);

      // 최근 데이터 샘플
      if (stockKeys != null && !stockKeys.isEmpty()) {
        List<String> cachedStocks = stockKeys.stream()
            .map(key -> key.replace("realtime:stock:", ""))
            .sorted()
            .toList();
        result.put("cachedStocks", cachedStocks);

        // 샘플 데이터 (가장 최근 것)
        String sampleKey = stockKeys.iterator().next();
        Object sampleData = redisTemplate.opsForValue().get(sampleKey);
        result.put("sampleStockData", sampleData);
      }

      result.put("timestamp", LocalDateTime.now().toString());

      return ResponseEntity.ok(result);

    } catch (Exception e) {
      log.error("캐시 상태 확인 실패", e);
      return ResponseEntity.internalServerError()
          .body(Map.of(
              "status", "ERROR",
              "message", e.getMessage(),
              "timestamp", LocalDateTime.now().toString()
          ));

    }
  }

  // 승인키 테스트
  @GetMapping("/approval-key")
  public ResponseEntity<Map<String, Object>> testApprovalKey() {
    try {
      String approvalKey = webSocketClient.getApprovalKey();

      Map<String, Object> result = new HashMap<>();
      result.put("status", "SUCCESS");
      result.put("approvalKey", approvalKey != null ? "발급됨" : "발급실패");
      result.put("keyLength", approvalKey != null ? approvalKey.length() : 0);
      result.put("timestamp", LocalDateTime.now().toString());

      if (approvalKey != null) {
        result.put("keyPreview", approvalKey.substring(0, Math.min(8, approvalKey.length())) + "...");
        result.put("message", "승인키 발급 정상");
      } else {
        result.put("message", "승인키 발급 실패");
      }

      // TODO: 발급 중 오류 처리가 이곳에서 필요한가?
      //  - 필요하다면 try-catch문으로 변경

      return ResponseEntity.ok(result);

    } catch (Exception e) {
      log.error("승인키 테스트 실패", e);
      return ResponseEntity.internalServerError()
          .body(Map.of(
              "status", "ERROR",
              "message", e.getMessage(),
              "timestamp", LocalDateTime.now().toString()
          ));
    }
  }

  // 웹소켓 메시지 통계
  @GetMapping("/message-stats")
  public ResponseEntity<MessageStats> getMessageStats() {
    try {
      // Redis에서 통계 데이터 조회
      String statsKey = "websocket:stats";
      Map<Object, Object> stats = redisTemplate.opsForHash().entries(statsKey);

      // 기본값 설정
      long totalMessages = parseLong(stats.get("totalMessages"), 0L);
      long priceMessages = parseLong(stats.get("priceMessages"), 0L);
      long orderbookMessages = parseLong(stats.get("orderbookMessages"), 0L);
      long errorCount = parseLong(stats.get("errorCount"), 0L);

      // lastMessageTime 파싱 개선
      LocalDateTime lastMessageTime = null;
      Object lastMessageTimeObj = stats.get("lastMessageTime");

      if (lastMessageTimeObj != null && !lastMessageTimeObj.toString().isEmpty()) {
        try {
          lastMessageTime = LocalDateTime.parse(lastMessageTimeObj.toString());
        } catch (Exception e) {
          log.warn("lastMessageTime 파싱 실패: {}", lastMessageTimeObj, e);
        }
      }

      MessageStats result = MessageStats.builder()
          .totalMessages(totalMessages)
          .priceMessages(priceMessages)
          .orderbookMessages(orderbookMessages)
          .errorCount(errorCount)
          .lastMessageTime(lastMessageTime)
          .timestamp(LocalDateTime.now())
          .build();

      return ResponseEntity.ok(result);
    } catch (Exception e) {
      log.error("메시지 통계 조회 실패", e);

      MessageStats errorStats = MessageStats.builder()
          .totalMessages(0L)
          .priceMessages(0L)
          .orderbookMessages(0L)
          .errorCount(1L)
          .lastMessageTime(null)
          .timestamp(LocalDateTime.now())
          .build();

      return ResponseEntity.internalServerError().body(errorStats);
    }
  }

  // 파싱 메서드
  private long parseLong(Object value, long defaultValue) {
    if (value == null) return defaultValue;
    try {
      return Long.parseLong(value.toString());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }
}