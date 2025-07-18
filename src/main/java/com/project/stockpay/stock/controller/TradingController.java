package com.project.stockpay.stock.controller;

import com.project.stockpay.common.entity.StockBuy;
import com.project.stockpay.common.entity.StockSell;
import com.project.stockpay.stock.dto.BuyOrderRequestDto;
import com.project.stockpay.stock.dto.SellOrderRequestDto;
import com.project.stockpay.stock.service.TradingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 주식 거래 API 컨트롤러
 * - 매수/매도 주문 접수
 * - 주문 체결 처리
 * - 대기 주문 조회
 */
@RestController
@RequestMapping("/trading")
@RequiredArgsConstructor
@Slf4j
public class TradingController {

  private final TradingService tradingService;

  /**
   * 매수 주문 접수
   * POST /trading/buy
   */
  @PostMapping("/buy")
  public ResponseEntity<Map<String, Object>> submitBuyOrder(
      @RequestBody BuyOrderRequestDto request) {
    log.info("매수 주문 접수 요청: {}", request);

    Map<String, Object> response = new HashMap<>();

    try {
      // 입력 검증
      validateBuyOrderRequest(request);

      // 매수 주문 접수
      String stockbuyId = tradingService.submitBuyOrder(
          request.getUserId(),
          request.getStockTicker(),
          request.getQuantity(),
          // default: 장내 시간-실시간 거래가, 장외시간-전일종가
          // 사용자 입력 시 사용자 입력 값
          request.getPrice()
      );

      response.put("success", true);
      response.put("message", "매수 주문이 성공적으로 접수되었습니다.");
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
      log.error("매수 주문 접수 실패: {}", e.getMessage());
      response.put("success", false);
      response.put("message", e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
  }

  /**
   * 매도 주문 접수 POST /trading/sell
   */
  @PostMapping("/sell")
  public ResponseEntity<Map<String, Object>> submitSellOrder(
      @RequestBody SellOrderRequestDto request) {
    log.info("매도 주문 접수 요청: {}", request);

    Map<String, Object> response = new HashMap<>();

    try {
      // 입력 검증
      validateSellOrderRequest(request);

      // 매도 주문 접수
      String stocksellId = tradingService.submitSellOrder(
          request.getUserId(),
          request.getStockTicker(),
          request.getQuantity(),
          // default: 장내 시간-실시간 거래가, 장외시간-전일종가
          // 사용자 입력 시 사용자 입력 값
          request.getPrice()
      );

      response.put("success", true);
      response.put("message", "매도 주문이 성공적으로 접수되었습니다.");
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
      log.error("매도 주문 접수 실패: {}", e.getMessage());
      response.put("success", false);
      response.put("message", e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
  }

  /**
   * 매수 주문 체결 처리
   * POST /trading/execute-buy/{stockbuyId}
   */
  @PostMapping("/execute-buy/{stockbuyId}")
  public ResponseEntity<Map<String, Object>> executeBuyOrder(@PathVariable String stockbuyId) {
    log.info("매수 주문 체결 처리 요청: stockbuyId={}", stockbuyId);

    Map<String, Object> response = new HashMap<>();

    try {
      tradingService.processBuyOrder(stockbuyId);

      response.put("success", true);
      response.put("message", "매수 주문 체결 처리가 완료되었습니다.");
      response.put("stockbuyId", stockbuyId);

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      log.error("매수 주문 체결 처리 실패: {}", e.getMessage());
      response.put("success", false);
      response.put("message", e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
  }

  /**
   * 매도 주문 체결 처리
   * POST /trading/execute-sell/{stocksellId}
   */
  @PostMapping("/execute-sell/{stocksellId}")
  public ResponseEntity<Map<String, Object>> executeSellOrder(@PathVariable String stocksellId) {
    log.info("매도 주문 체결 처리 요청: stocksellId={}", stocksellId);

    Map<String, Object> response = new HashMap<>();

    try {
      tradingService.processSellOrder(stocksellId);

      response.put("success", true);
      response.put("message", "매도 주문 체결 처리가 완료되었습니다.");
      response.put("stocksellId", stocksellId);

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      log.error("매도 주문 체결 처리 실패: {}", e.getMessage());
      response.put("success", false);
      response.put("message", e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
  }

  /**
   * 대기 중인 매수 주문 조회
   * GET /trading/{userId}/pending-buy
   */
  @GetMapping("/{userId}/pending-buy")
  public ResponseEntity<Map<String, Object>> getPendingBuyOrders(@PathVariable String userId) {
    log.info("대기 중인 매수 주문 조회: userId={}", userId);

    Map<String, Object> response = new HashMap<>();

    try {
      List<StockBuy> pendingBuyOrders = tradingService.getPendingBuyOrders(userId);

      response.put("success", true);
      response.put("message", "대기 중인 매수 주문을 조회했습니다.");
      response.put("userId", userId);
      response.put("pendingBuyOrders", pendingBuyOrders);
      response.put("count", pendingBuyOrders.size());

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      log.error("대기 중인 매수 주문 조회 실패: {}", e.getMessage());
      response.put("success", false);
      response.put("message", e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
  }

  /**
   * 대기 중인 매도 주문 조회
   * GET /trading/{userId}/pending-sell
   */
  @GetMapping("/{userId}/pending-sell")
  public ResponseEntity<Map<String, Object>> getPendingSellOrders(@PathVariable String userId) {
    log.info("대기 중인 매도 주문 조회: userId={}", userId);

    Map<String, Object> response = new HashMap<>();

    try {
      List<StockSell> pendingSellOrders = tradingService.getPendingSellOrders(userId);

      response.put("success", true);
      response.put("message", "대기 중인 매도 주문을 조회했습니다.");
      response.put("userId", userId);
      response.put("pendingSellOrders", pendingSellOrders);
      response.put("count", pendingSellOrders.size());

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      log.error("대기 중인 매도 주문 조회 실패: {}", e.getMessage());
      response.put("success", false);
      response.put("message", e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
  }

  // 입력 검증 메서드

  /**
   * 공통 주문 요청 검증 메서드
   */
  private void validateOrderRequest(String userId, String stockTicker, Integer quantity,
      Integer price, String orderType) {
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

    log.debug("{} 주문 요청 검증 완료: userId={}, stockTicker={}, quantity={}, price={}",
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