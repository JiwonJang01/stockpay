package com.project.stockpay.common.websocket.controller;

import com.project.stockpay.common.websocket.KisWebSocketClient;
import com.project.stockpay.common.websocket.dto.RealTimeStockPriceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/websocket")
@Slf4j
@RequiredArgsConstructor
public class WebSocketController {

  private final KisWebSocketClient webSocketClient;
  private final RedisTemplate<String, Object> redisTemplate;

  // 연결 상태 확인
  @GetMapping("/status")
  public ResponseEntity<Map<String, Object>> getConnectionStatus() {
    boolean connected = webSocketClient.isConnected();

    return ResponseEntity.ok(Map.of(
        "connected", connected,
        "timestamp", System.currentTimeMillis(),
        "message", connected ? "웹소켓 연결됨" : "웹소켓 연결 안됨"
    ));
  }

  // 종목 구독
  @PostMapping("/subscribe/price/{stockCode}")
  public ResponseEntity<String> subscribeStockPrice(@PathVariable String stockCode) {
    try {
      webSocketClient.subscribeStockPrice(stockCode);
      return ResponseEntity.ok("종목 " + stockCode + " 실시간 체결가 구독 요청 완료");
    } catch (Exception e) {
      log.error("구독 요청 실패: {}", stockCode, e);
      return ResponseEntity.badRequest().body("구독 요청 실패: " + e.getMessage());
    }
  }

  // 호가 구독
  @PostMapping("/subscribe/orderbook/{stockCode}")
  public ResponseEntity<String> subscribeStockOrderbook(@PathVariable String stockCode) {
    try {
      webSocketClient.subscribeStockOrderbook(stockCode);
      return ResponseEntity.ok("종목 " + stockCode + " 실시간 호가 구독 요청 완료");
    } catch (Exception e) {
      log.error("호가 구독 실패: {}", stockCode, e);
      return ResponseEntity.badRequest().body("호가 구독 실패: " + e.getMessage());
    }
  }

  // 구독 해제
  @DeleteMapping("/unsubscribe/price/{stockCode}")
  public ResponseEntity<String> unsubscribeStockPrice(@PathVariable String stockCode) {
    try {
      webSocketClient.unsubscribe("H0STCNT0", stockCode);
      return ResponseEntity.ok("종목 " + stockCode + " 실시간 체결가 구독 해제 완료");
    } catch (Exception e) {
      log.error("구독 해제 실패: {}", stockCode, e);
      return ResponseEntity.badRequest().body("구독 해제 실패: " + e.getMessage());
    }
  }

  // Redis에서 실시간 데이터 조회
  @GetMapping("/realtime/price/{stockCode}")
  public ResponseEntity<RealTimeStockPriceDto> getRealtimePrice(@PathVariable String stockCode) {
    try {
      String cacheKey = "realtime:stock:" + stockCode;
      RealTimeStockPriceDto data = (RealTimeStockPriceDto) redisTemplate.opsForValue()
          .get(cacheKey);

      if (data != null) {
        return ResponseEntity.ok(data);
      } else {
        return ResponseEntity.notFound().build();
      }
    } catch (Exception e) {
      log.error("실시간 데이터 조회 실패: {}", stockCode, e);
      return ResponseEntity.internalServerError().build();
    }
  }
}