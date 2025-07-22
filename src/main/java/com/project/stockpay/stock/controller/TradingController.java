package com.project.stockpay.stock.controller;

import com.project.stockpay.stock.dto.BuyOrderRequestDto;
import com.project.stockpay.stock.dto.OrderRetryStatus;
import com.project.stockpay.stock.dto.SellOrderRequestDto;
import com.project.stockpay.stock.service.TradingService;
import com.project.stockpay.stock.service.StockOrderService;
import com.project.stockpay.stock.service.StockPriceService;
import com.project.stockpay.stock.service.StockStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 모의투자 매매 거래 컨트롤러
 * - 매수/매도 주문 처리
 * - 주문 상태 모니터링
 * - 예약 주문 관리
 * - 확률적 체결 시스템 (65~75% 확률, 3분 재시도, 5회 후 100% 체결)
 */
@RestController
@RequestMapping("/trading")
@RequiredArgsConstructor
@Slf4j
public class TradingController {

  private final TradingService tradingService;
  private final StockOrderService stockOrderService;
  private final StockPriceService stockPriceService;
  private final StockStatusService stockStatusService;

  // TODO: Map<String, Object> 형식 수정
  // ========== 매수 주문 처리 ==========

  /**
   * 매수 주문 접수 (지정가)
   */
  @PostMapping("/buy")
  public ResponseEntity<Map<String, Object>> submitBuyOrder(
      @RequestBody BuyOrderRequestDto request) {

    log.info("매수 주문 접수: userId={}, stockTicker={}, quantity={}, price={}",
        request.getUserId(), request.getStockTicker(), request.getQuantity(), request.getPrice());

    try {
      String stockbuyId = tradingService.submitBuyOrder(request);

      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      response.put("orderId", stockbuyId);
      response.put("orderType", "BUY");
      response.put("message", "매수 주문이 접수되었습니다");
      response.put("userId", request.getUserId());
      response.put("stockTicker", request.getStockTicker());
      response.put("quantity", request.getQuantity());
      response.put("price", request.getPrice());
      response.put("expectedExecution", "65~75% 확률로 체결, 미체결 시 3분 후 재시도");
      response.put("isMarketOpen", stockStatusService.isMarketOpen());
      response.put("timestamp", System.currentTimeMillis());

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      log.error("매수 주문 접수 실패: userId={}, error={}", request.getUserId(), e.getMessage());

      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("success", false);
      errorResponse.put("orderType", "BUY");
      errorResponse.put("message", "매수 주문 접수 실패: " + e.getMessage());
      errorResponse.put("userId", request.getUserId());
      errorResponse.put("timestamp", System.currentTimeMillis());

      return ResponseEntity.badRequest().body(errorResponse);
    }
  }

  /**
   * 시장가 매수 주문 (현재가로 즉시 주문)
   */
  @PostMapping("/buy/market")
  public ResponseEntity<Map<String, Object>> submitBuyOrderAtMarketPrice(
      @RequestParam String userId,
      @RequestParam String stockTicker,
      @RequestParam Integer quantity) {

    log.info("시장가 매수 주문: userId={}, stockTicker={}, quantity={}",
        userId, stockTicker, quantity);

    try {
      String stockbuyId = tradingService.submitBuyOrderAtMarketPrice(userId, stockTicker, quantity);

      // 현재가 조회
      Integer currentPrice = stockPriceService.getCurrentPrice(stockTicker);

      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      response.put("orderId", stockbuyId);
      response.put("orderType", "BUY_MARKET");
      response.put("message", "시장가 매수 주문이 접수되었습니다");
      response.put("userId", userId);
      response.put("stockTicker", stockTicker);
      response.put("quantity", quantity);
      response.put("marketPrice", currentPrice);
      response.put("totalAmount", currentPrice * quantity);
      response.put("expectedExecution", "65~75% 확률로 체결, 미체결 시 3분 후 재시도");
      response.put("isMarketOpen", stockStatusService.isMarketOpen());
      response.put("timestamp", System.currentTimeMillis());

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      log.error("시장가 매수 주문 실패: userId={}, error={}", userId, e.getMessage());

      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("success", false);
      errorResponse.put("orderType", "BUY_MARKET");
      errorResponse.put("message", "시장가 매수 주문 실패: " + e.getMessage());
      errorResponse.put("userId", userId);
      errorResponse.put("timestamp", System.currentTimeMillis());

      return ResponseEntity.badRequest().body(errorResponse);
    }
  }

  // ========== 매도 주문 처리 ==========

  /**
   * 매도 주문 접수 (지정가)
   */
  @PostMapping("/sell")
  public ResponseEntity<Map<String, Object>> submitSellOrder(
      @RequestBody SellOrderRequestDto request) {

    log.info("매도 주문 접수: userId={}, stockTicker={}, quantity={}, price={}",
        request.getUserId(), request.getStockTicker(), request.getQuantity(), request.getPrice());

    try {
      String stocksellId = tradingService.submitSellOrder(request);

      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      response.put("orderId", stocksellId);
      response.put("orderType", "SELL");
      response.put("message", "매도 주문이 접수되었습니다");
      response.put("userId", request.getUserId());
      response.put("stockTicker", request.getStockTicker());
      response.put("quantity", request.getQuantity());
      response.put("price", request.getPrice());
      response.put("expectedExecution", "65~75% 확률로 체결, 미체결 시 3분 후 재시도");
      response.put("isMarketOpen", stockStatusService.isMarketOpen());
      response.put("timestamp", System.currentTimeMillis());

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      log.error("매도 주문 접수 실패: userId={}, error={}", request.getUserId(), e.getMessage());

      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("success", false);
      errorResponse.put("orderType", "SELL");
      errorResponse.put("message", "매도 주문 접수 실패: " + e.getMessage());
      errorResponse.put("userId", request.getUserId());
      errorResponse.put("timestamp", System.currentTimeMillis());

      return ResponseEntity.badRequest().body(errorResponse);
    }
  }

  /**
   * 시장가 매도 주문 (현재가로 즉시 주문)
   */
  @PostMapping("/sell/market")
  public ResponseEntity<Map<String, Object>> submitSellOrderAtMarketPrice(
      @RequestParam String userId,
      @RequestParam String stockTicker,
      @RequestParam Integer quantity) {

    log.info("시장가 매도 주문: userId={}, stockTicker={}, quantity={}",
        userId, stockTicker, quantity);

    try {
      String stocksellId = tradingService.submitSellOrderAtMarketPrice(userId, stockTicker, quantity);

      // 현재가 조회
      Integer currentPrice = stockPriceService.getCurrentPrice(stockTicker);

      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      response.put("orderId", stocksellId);
      response.put("orderType", "SELL_MARKET");
      response.put("message", "시장가 매도 주문이 접수되었습니다");
      response.put("userId", userId);
      response.put("stockTicker", stockTicker);
      response.put("quantity", quantity);
      response.put("marketPrice", currentPrice);
      response.put("totalAmount", currentPrice * quantity);
      response.put("expectedExecution", "65~75% 확률로 체결, 미체결 시 3분 후 재시도");
      response.put("isMarketOpen", stockStatusService.isMarketOpen());
      response.put("timestamp", System.currentTimeMillis());

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      log.error("시장가 매도 주문 실패: userId={}, error={}", userId, e.getMessage());

      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("success", false);
      errorResponse.put("orderType", "SELL_MARKET");
      errorResponse.put("message", "시장가 매도 주문 실패: " + e.getMessage());
      errorResponse.put("userId", userId);
      errorResponse.put("timestamp", System.currentTimeMillis());

      return ResponseEntity.badRequest().body(errorResponse);
    }
  }

  // ========== 주문 상태 조회 및 모니터링 ==========

  /**
   * 주문 재시도 상태 조회 (확률적 체결 시스템 모니터링)
   */
  @GetMapping("/order/retry-status/{orderId}")
  public ResponseEntity<Map<String, Object>> getOrderRetryStatus(
      @PathVariable String orderId) {

    log.info("주문 재시도 상태 조회: orderId={}", orderId);

    OrderRetryStatus retryStatus = stockOrderService.getRetryStatus(orderId);

    Map<String, Object> response = new HashMap<>();
    response.put("orderId", retryStatus.getOrderId());
    response.put("retryCount", retryStatus.getRetryCount());
    response.put("maxRetryCount", retryStatus.getMaxRetryCount());
    response.put("isMaxRetryReached", retryStatus.isMaxRetryReached());
    response.put("nextRetryTime", retryStatus.getNextRetryTime());
    response.put("status", retryStatus.isMaxRetryReached() ? "FORCE_EXECUTION_NEXT" : "RETRY_PENDING");
    response.put("message", retryStatus.isMaxRetryReached() ?
        "다음 시도에서 100% 체결됩니다" :
        String.format("재시도 %d/%d - 3분 후 다시 시도", retryStatus.getRetryCount(), retryStatus.getMaxRetryCount()));
    response.put("timestamp", System.currentTimeMillis());

    return ResponseEntity.ok(response);
  }

  /**
   * 거래 시스템 상태 조회
   */
  @GetMapping("/system/status")
  public ResponseEntity<Map<String, Object>> getTradingSystemStatus() {

    log.info("거래 시스템 상태 조회");

    try {
      boolean isMarketOpen = stockStatusService.isMarketOpen();
      Map<String, Object> dataQuality = stockStatusService.checkDataQuality();
      String dataQualityStatus = (String) dataQuality.get("status");

      // 거래 시스템 상태 판단
      boolean tradingSystemHealthy = !"CRITICAL".equals(dataQualityStatus) &&
          !"ERROR".equals(dataQualityStatus);

      Map<String, Object> response = new HashMap<>();
      response.put("systemStatus", tradingSystemHealthy ? "HEALTHY" : "DEGRADED");
      response.put("tradingEnabled", true); // 모의투자는 항상 거래 가능
      response.put("executionProbability", "65~75%");

      // 재시도 메커니즘 정보
      Map<String, Object> retryMechanism = Map.of(
          "enabled", true,
          "retryInterval", "3분",
          "maxRetries", 5,
          "forceExecutionAfter", "5회 재시도 후 100% 체결"
      );
      response.put("retryMechanism", retryMechanism);

      // 시장 정보
      Map<String, Object> market = Map.of(
          "isOpen", isMarketOpen,
          "orderMode", isMarketOpen ? "즉시 처리" : "예약 주문",
          "dataQuality", dataQualityStatus
      );
      response.put("market", market);

      // 서비스 상태
      Map<String, Object> services = Map.of(
          "orderProcessing", "RUNNING",
          "kafkaConsumer", "RUNNING",
          "retryQueue", "RUNNING"
      );
      response.put("services", services);
      response.put("timestamp", System.currentTimeMillis());

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      log.error("거래 시스템 상태 조회 실패", e);

      Map<String, Object> errorResponse = Map.of(
          "systemStatus", "ERROR",
          "message", "거래 시스템 상태 조회 실패: " + e.getMessage(),
          "timestamp", System.currentTimeMillis()
      );

      return ResponseEntity.status(500).body(errorResponse);
    }
  }

  // ========== 예약 주문 관리 ==========

  /**
   * 예약 주문 처리 (개장 시 자동 실행)
   */
  @PostMapping("/orders/process-reserved")
  public ResponseEntity<Map<String, Object>> processReservedOrders() {

    log.info("예약 주문 처리 요청");

    try {
      tradingService.processReservedOrders();

      Map<String, Object> response = Map.of(
          "success", true,
          "message", "예약 주문이 처리되어 일반 주문으로 전환되었습니다",
          "note", "전환된 주문들은 65~75% 확률로 체결 시도됩니다",
          "timestamp", System.currentTimeMillis()
      );

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      log.error("예약 주문 처리 실패", e);

      Map<String, Object> errorResponse = Map.of(
          "success", false,
          "message", "예약 주문 처리 실패: " + e.getMessage(),
          "timestamp", System.currentTimeMillis()
      );

      return ResponseEntity.ok(errorResponse);
    }
  }

  // ========== 테스트 및 개발 지원 ==========

  /**
   * 거래 시스템 초기화 (테스트용)
   */
  @PostMapping("/system/initialize")
  public ResponseEntity<Map<String, Object>> initializeTradingSystem() {

    log.info("거래 시스템 초기화 요청");

    try {
      // 테스트용 전일 종가 설정
      stockStatusService.setClosePricesForTesting();

      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      response.put("message", "거래 시스템 초기화 완료");
      response.put("actions", List.of(
          "테스트용 주가 데이터 설정 완료",
          "모의투자 거래 환경 준비 완료"
      ));

      Map<String, Object> tradingInfo = Map.of(
          "executionProbability", "65~75%",
          "retryInterval", "3분",
          "maxRetries", 5,
          "forceExecution", "5회 후 100% 체결"
      );
      response.put("tradingInfo", tradingInfo);
      response.put("timestamp", System.currentTimeMillis());

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      log.error("거래 시스템 초기화 실패", e);

      Map<String, Object> errorResponse = Map.of(
          "success", false,
          "message", "거래 시스템 초기화 실패: " + e.getMessage(),
          "timestamp", System.currentTimeMillis()
      );

      return ResponseEntity.ok(errorResponse);
    }
  }

  /**
   * 거래 서비스 헬스체크
   */
  @GetMapping("/health")
  public ResponseEntity<Map<String, Object>> healthCheck() {

    log.info("거래 서비스 헬스체크");

    try {
      // 기본 서비스 상태 확인
      Map<String, Object> healthStatus = stockStatusService.healthCheck();
      String status = (String) healthStatus.get("status");

      // 거래 특화 정보 추가
      Map<String, Object> tradingServices = Map.of(
          "orderProcessing", "RUNNING",
          "probabilisticExecution", "ACTIVE",
          "retryMechanism", "ACTIVE",
          "reservedOrderProcessing", "ACTIVE"
      );

      Map<String, Object> tradingConfig = Map.of(
          "executionRate", "65~75%",
          "retryInterval", "3분",
          "maxRetries", 5
      );

      Map<String, Object> tradingHealth = Map.of(
          "baseHealth", healthStatus,
          "tradingServices", tradingServices,
          "tradingConfig", tradingConfig
      );

      return switch (status) {
        case "HEALTHY" -> ResponseEntity.ok(tradingHealth);
        case "DEGRADED" -> ResponseEntity.status(206).body(tradingHealth);
        default -> ResponseEntity.status(503).body(tradingHealth);
      };

    } catch (Exception e) {
      log.error("거래 서비스 헬스체크 실패", e);

      Map<String, Object> errorResponse = Map.of(
          "status", "ERROR",
          "message", "거래 서비스 헬스체크 실패: " + e.getMessage(),
          "timestamp", System.currentTimeMillis()
      );

      return ResponseEntity.status(500).body(errorResponse);
    }
  }
}