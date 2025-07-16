package com.project.stockpay.stock;

import com.project.stockpay.stock.dto.BuyOrderRequestDto;
import com.project.stockpay.stock.dto.SellOrderRequestDto;
import com.project.stockpay.stock.service.TradingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/test/trading")
@RequiredArgsConstructor
@Slf4j
public class TradingTestController {

  private final TradingService tradingService;

  /**
   * 테스트용 매수 주문
   * 접수 즉시 체결
   * POST /test/trading/buy
   */
  @PostMapping("/buy")
  public ResponseEntity<Map<String, Object>> testBuyOrder(@RequestBody BuyOrderRequestDto request) {
    log.info("테스트 매수 주문 요청: {}", request);

    Map<String, Object> response = new HashMap<>();

    try {
      // 입력 검증
      validateBuyOrderRequest(request);

      // 매수 주문 접수 및 즉시 체결
      String stockbuyId = tradingService.submitAndProcessBuyOrder(
          request.getUserId(),
          request.getStockTicker(),
          request.getQuantity(),
          request.getPrice()
      );

      response.put("success", true);
      response.put("message", "테스트 매수 주문이 성공적으로 처리되었습니다.");
      response.put("stockbuyId", stockbuyId);
      response.put("orderInfo", Map.of(
          "userId", request.getUserId(),
          "stockTicker", request.getStockTicker(),
          "quantity", request.getQuantity(),
          "price", request.getPrice(),
          "totalAmount", request.getPrice() * request.getQuantity()
      ));

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      log.error("테스트 매수 주문 실패: {}", e.getMessage());
      response.put("success", false);
      response.put("message", e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
  }

  /**
   * 테스트용 매도 주문
   * 접수 즉시 체결
   * POST /test/trading/sell
   */
  @PostMapping("/sell")
  public ResponseEntity<Map<String, Object>> testSellOrder(@RequestBody SellOrderRequestDto request) {
    log.info("테스트 매도 주문 요청: {}", request);

    Map<String, Object> response = new HashMap<>();

    try {
      // 입력 검증
      validateSellOrderRequest(request);

      // 매도 주문 접수 및 즉시 체결
      String stocksellId = tradingService.submitAndProcessSellOrder(
          request.getUserId(),
          request.getStockTicker(),
          request.getQuantity(),
          request.getPrice()
      );

      response.put("success", true);
      response.put("message", "테스트 매도 주문이 성공적으로 처리되었습니다.");
      response.put("stocksellId", stocksellId);
      response.put("orderInfo", Map.of(
          "userId", request.getUserId(),
          "stockTicker", request.getStockTicker(),
          "quantity", request.getQuantity(),
          "price", request.getPrice(),
          "totalAmount", request.getPrice() * request.getQuantity()
      ));

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      log.error("테스트 매도 주문 실패: {}", e.getMessage());
      response.put("success", false);
      response.put("message", e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
  }

  /**
   * 간편 테스트용 삼성전자 매수
   * POST /test/trading/buy-samsung/{userId}?quantity=1&price=70000
   */
  @PostMapping("/buy-samsung/{userId}")
  public ResponseEntity<Map<String, Object>> testBuySamsung(
      @PathVariable String userId,
      @RequestParam(defaultValue = "1") Integer quantity,
      @RequestParam(defaultValue = "70000") Integer price) {

    log.info("간편 삼성전자 매수 테스트: userId={}, quantity={}, price={}", userId, quantity, price);

    Map<String, Object> response = new HashMap<>();

    try {
      // 삼성전자 매수 주문 접수 및 즉시 체결
      String stockbuyId = tradingService.submitAndProcessBuyOrder(
          userId,
          "005930", // 삼성전자 종목코드
          quantity,
          price
      );

      response.put("success", true);
      response.put("message", "삼성전자 매수가 성공적으로 처리되었습니다.");
      response.put("stockbuyId", stockbuyId);
      response.put("orderInfo", Map.of(
          "userId", userId,
          "stockTicker", "005930",
          "stockName", "삼성전자",
          "quantity", quantity,
          "price", price,
          "totalAmount", price * quantity
      ));

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      log.error("삼성전자 매수 테스트 실패: {}", e.getMessage());
      response.put("success", false);
      response.put("message", e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
  }

  /**
   * 간편 테스트용 NAVER 매수
   * POST /test/trading/buy-naver/{userId}?quantity=1&price=200000
   */
  @PostMapping("/buy-naver/{userId}")
  public ResponseEntity<Map<String, Object>> testBuyNaver(
      @PathVariable String userId,
      @RequestParam(defaultValue = "1") Integer quantity,
      @RequestParam(defaultValue = "200000") Integer price) {

    log.info("간편 NAVER 매수 테스트: userId={}, quantity={}, price={}", userId, quantity, price);

    Map<String, Object> response = new HashMap<>();

    try {
      // NAVER 매수 주문 접수 및 즉시 체결
      String stockbuyId = tradingService.submitAndProcessBuyOrder(
          userId,
          "035420", // NAVER 종목코드
          quantity,
          price
      );

      response.put("success", true);
      response.put("message", "NAVER 매수가 성공적으로 처리되었습니다.");
      response.put("stockbuyId", stockbuyId);
      response.put("orderInfo", Map.of(
          "userId", userId,
          "stockTicker", "035420",
          "stockName", "NAVER",
          "quantity", quantity,
          "price", price,
          "totalAmount", price * quantity
      ));

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      log.error("NAVER 매수 테스트 실패: {}", e.getMessage());
      response.put("success", false);
      response.put("message", e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
  }

  /**
   * 멀티 주문 테스트
   * POST /test/trading/multi-buy/{userId}
   */
  @PostMapping("/multi-buy/{userId}")
  public ResponseEntity<Map<String, Object>> testMultiBuy(@PathVariable String userId) {
    log.info("멀티 주문 테스트: userId={}", userId);

    Map<String, Object> response = new HashMap<>();
    Map<String, Object> results = new HashMap<>();

    try {
      // 삼성전자 1주 매수
      String samsungOrderId = tradingService.submitAndProcessBuyOrder(
          userId, "005930", 1, 70000);
      results.put("samsung", Map.of("orderId", samsungOrderId, "success", true));

      // SK하이닉스 1주 매수
      String skOrderId = tradingService.submitAndProcessBuyOrder(
          userId, "000660", 1, 120000);
      results.put("sk", Map.of("orderId", skOrderId, "success", true));

      // NAVER 1주 매수
      String naverOrderId = tradingService.submitAndProcessBuyOrder(
          userId, "035420", 1, 200000);
      results.put("naver", Map.of("orderId", naverOrderId, "success", true));

      response.put("success", true);
      response.put("message", "멀티 주문이 성공적으로 처리되었습니다.");
      response.put("userId", userId);
      response.put("results", results);
      response.put("totalAmount", 390000); // 70000 + 120000 + 200000

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      log.error("멀티 주문 테스트 실패: {}", e.getMessage());
      response.put("success", false);
      response.put("message", e.getMessage());
      response.put("results", results);
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
  }

  /**
   * 체결 확률 테스트 (10번 연속 주문)
   * POST /test/trading/probability-test/{userId}
   */
  @PostMapping("/probability-test/{userId}")
  public ResponseEntity<Map<String, Object>> testExecutionProbability(@PathVariable String userId) {
    log.info("체결 확률 테스트: userId={}", userId);

    Map<String, Object> response = new HashMap<>();
    int successCount = 0;
    int totalCount = 10;

    try {
      for (int i = 0; i < totalCount; i++) {
        try {
          // 소액 주문으로 테스트 (1000원 x 1주)
          tradingService.submitAndProcessBuyOrder(userId, "005930", 1, 1000);
          successCount++;
        } catch (Exception e) {
          // 체결 실패는 정상적인 동작
          log.debug("체결 실패 ({}회차): {}", i + 1, e.getMessage());
        }
      }

      double successRate = (double) successCount / totalCount * 100;

      response.put("success", true);
      response.put("message", "체결 확률 테스트가 완료되었습니다.");
      response.put("userId", userId);
      response.put("totalOrders", totalCount);
      response.put("successfulOrders", successCount);
      response.put("successRate", String.format("%.1f%%", successRate));
      response.put("expectedRate", "65-75%");

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      log.error("체결 확률 테스트 실패: {}", e.getMessage());
      response.put("success", false);
      response.put("message", e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
  }

  // 요청 검증

  /**
   * 공통 주문 요청 검증 메서드
   */
  private void validateOrderRequest(String userId, String stockTicker, Integer quantity, Integer price, String orderType) {
    if (userId == null || userId.trim().isEmpty()) {
      throw new IllegalArgumentException("사용자 ID는 필수입니다.");
    }
    if (stockTicker == null || stockTicker.trim().isEmpty()) {
      throw new IllegalArgumentException("종목 티커는 필수입니다.");
    }
    if (quantity == null || quantity <= 0) {
      throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
    }
    if (price == null || price <= 0) {
      throw new IllegalArgumentException("가격은 1 이상이어야 합니다.");
    }

    log.debug("테스트 {} 주문 요청 검증 완료: userId={}, stockTicker={}, quantity={}, price={}",
        orderType, userId, stockTicker, quantity, price);
  }

  /**
   * 매수 주문 요청 검증
   */
  private void validateBuyOrderRequest(BuyOrderRequestDto request) {
    validateOrderRequest(request.getUserId(), request.getStockTicker(),
        request.getQuantity(), request.getPrice(), "매수");
  }

  /**
   * 매도 주문 요청 검증
   */
  private void validateSellOrderRequest(SellOrderRequestDto request) {
    validateOrderRequest(request.getUserId(), request.getStockTicker(),
        request.getQuantity(), request.getPrice(), "매도");
  }
}
