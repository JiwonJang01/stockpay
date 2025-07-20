package com.project.stockpay.stock.controller;

import com.project.stockpay.common.entity.Stock;
import com.project.stockpay.common.websocket.dto.RealTimeOrderbookDto;
import com.project.stockpay.common.websocket.dto.RealTimeStockPriceDto;
import com.project.stockpay.stock.dto.StockPriceInfoDto;
import com.project.stockpay.stock.service.StockPriceService;
import com.project.stockpay.stock.service.StockStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 주식 정보 조회 컨트롤러
 * - 실시간 주가/호가 데이터 조회
 * - 종목 정보 및 검색
 * - 시장 상태 정보
 */
@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
@Slf4j
public class StockController {

  private final StockPriceService stockPriceService;
  private final StockStatusService stockStatusService;

  // ========== 실시간 주가 정보 조회 ==========

  /**
   * 실시간 주가 조회
   */
  @GetMapping("/price/realtime/{stockTicker}")
  public ResponseEntity<RealTimeStockPriceDto> getRealTimeStockPrice(
      @PathVariable String stockTicker) {

    log.info("실시간 주가 조회: stockTicker={}", stockTicker);

    RealTimeStockPriceDto priceData = stockPriceService.getRealTimeStockPrice(stockTicker);

    if (priceData != null) {
      return ResponseEntity.ok(priceData);
    } else {
      return ResponseEntity.notFound().build();
    }
  }

  /**
   * 실시간 호가 조회
   */
  @GetMapping("/price/orderbook/{stockTicker}")
  public ResponseEntity<RealTimeOrderbookDto> getRealTimeOrderbook(
      @PathVariable String stockTicker) {

    log.info("실시간 호가 조회: stockTicker={}", stockTicker);

    RealTimeOrderbookDto orderbookData = stockPriceService.getRealTimeOrderbook(stockTicker);

    if (orderbookData != null) {
      return ResponseEntity.ok(orderbookData);
    } else {
      return ResponseEntity.notFound().build();
    }
  }

  /**
   * 현재가 조회
   */
  @GetMapping("/price/current/{stockTicker}")
  public ResponseEntity<Map<String, Object>> getCurrentPrice(
      @PathVariable String stockTicker) {

    log.info("현재가 조회: stockTicker={}", stockTicker);

    Integer currentPrice = stockPriceService.getCurrentPrice(stockTicker);

    if (currentPrice != null) {
      return ResponseEntity.ok(Map.of(
          "stockTicker", stockTicker,
          "currentPrice", currentPrice,
          "isMarketOpen", stockStatusService.isMarketOpen(),
          "timestamp", System.currentTimeMillis()
      ));
    } else {
      return ResponseEntity.notFound().build();
    }
  }

  /**
   * 전일 종가 조회
   */
  @GetMapping("/price/close/{stockTicker}")
  public ResponseEntity<Map<String, Object>> getPreviousClosePrice(
      @PathVariable String stockTicker) {

    log.info("전일 종가 조회: stockTicker={}", stockTicker);

    Integer closePrice = stockPriceService.getPreviousClosePrice(stockTicker);

    return ResponseEntity.ok(Map.of(
        "stockTicker", stockTicker,
        "previousClosePrice", closePrice,
        "timestamp", System.currentTimeMillis()
    ));
  }

  /**
   * 종합 주가 정보 조회
   */
  @GetMapping("/info/{stockTicker}")
  public ResponseEntity<StockPriceInfoDto> getStockPriceInfo(
      @PathVariable String stockTicker) {

    log.info("종합 주가 정보 조회: stockTicker={}", stockTicker);

    StockPriceInfoDto stockInfo = stockPriceService.getStockPriceInfo(stockTicker);

    if (stockInfo != null) {
      return ResponseEntity.ok(stockInfo);
    } else {
      return ResponseEntity.notFound().build();
    }
  }

  /**
   * 여러 종목 주가 정보 조회
   */
  @PostMapping("/info/multiple")
  public ResponseEntity<List<StockPriceInfoDto>> getMultipleStockPrices(
      @RequestBody List<String> stockTickers) {

    log.info("다중 종목 주가 정보 조회: {}개 종목", stockTickers.size());

    List<StockPriceInfoDto> stockInfos = stockPriceService.getMultipleStockPrices(stockTickers);

    return ResponseEntity.ok(stockInfos);
  }

  /**
   * 주요 종목 주가 정보 조회
   */
  @GetMapping("/popular")
  public ResponseEntity<List<StockPriceInfoDto>> getPopularStockPrices() {

    log.info("주요 종목 주가 정보 조회");

    List<StockPriceInfoDto> popularStocks = stockPriceService.getPopularStockPrices();

    return ResponseEntity.ok(popularStocks);
  }

  /**
   * 섹터별 주가 정보 조회
   */
  @GetMapping("/sector/{sector}")
  public ResponseEntity<List<StockPriceInfoDto>> getStockPricesBySector(
      @PathVariable String sector) {

    log.info("섹터별 주가 정보 조회: sector={}", sector);

    List<StockPriceInfoDto> sectorStocks = stockPriceService.getStockPricesBySector(sector);

    return ResponseEntity.ok(sectorStocks);
  }

  /**
   * 종목 검색
   */
  @GetMapping("/search")
  public ResponseEntity<List<StockPriceInfoDto>> searchStocks(
      @RequestParam String keyword) {

    log.info("종목 검색: keyword={}", keyword);

    List<StockPriceInfoDto> searchResults = stockPriceService.searchStocks(keyword);

    return ResponseEntity.ok(searchResults);
  }

  // ========== 종목 기본 정보 ==========

  /**
   * 전체 종목 목록 조회
   */
  @GetMapping("/list/all")
  public ResponseEntity<List<Stock>> getAllStocks() {

    log.info("전체 종목 목록 조회");

    List<Stock> allStocks = stockPriceService.getAllStocks();

    return ResponseEntity.ok(allStocks);
  }

  /**
   * 상장 종목 목록 조회
   */
  @GetMapping("/list/listed")
  public ResponseEntity<List<Stock>> getListedStocks() {

    log.info("상장 종목 목록 조회");

    List<Stock> listedStocks = stockPriceService.getListedStocks();

    return ResponseEntity.ok(listedStocks);
  }

  /**
   * 활성 종목 목록 조회 (실시간 데이터가 있는 종목)
   */
  @GetMapping("/list/active")
  public ResponseEntity<List<String>> getActiveStockList() {

    log.info("활성 종목 목록 조회");

    List<String> activeStocks = stockPriceService.getActiveStockList();

    return ResponseEntity.ok(activeStocks);
  }

  // ========== 시장 상태 정보 ==========

  /**
   * 현재 장 상태 조회
   */
  @GetMapping("/market/status")
  public ResponseEntity<Map<String, Object>> getMarketStatus() {

    log.info("시장 상태 조회");

    boolean isMarketOpen = stockStatusService.isMarketOpen();
    Map<String, Object> detailStatus = stockStatusService.getMarketStatus();

    return ResponseEntity.ok(Map.of(
        "isMarketOpen", isMarketOpen,
        "tradingAvailable", true, // 모의투자는 항상 거래 가능
        "orderType", isMarketOpen ? "즉시 주문" : "예약 주문",
        "marketDetail", detailStatus,
        "timestamp", System.currentTimeMillis()
    ));
  }

  /**
   * 실시간 데이터 품질 상태
   */
  @GetMapping("/market/data-quality")
  public ResponseEntity<Map<String, Object>> getDataQuality() {

    log.info("데이터 품질 상태 조회");

    Map<String, Object> dataQuality = stockStatusService.checkDataQuality();

    return ResponseEntity.ok(dataQuality);
  }

  /**
   * 실시간 데이터 통계
   */
  @GetMapping("/market/data-stats")
  public ResponseEntity<Map<String, Object>> getRealTimeDataStats() {

    log.info("실시간 데이터 통계 조회");

    Map<String, Object> dataStats = stockStatusService.getRealTimeDataStats();

    return ResponseEntity.ok(dataStats);
  }

  // ========== 테스트 및 개발 지원 ==========

  /**
   * 종목별 기본 가격 조회 (테스트용)
   */
  @GetMapping("/price/default/{stockTicker}")
  public ResponseEntity<Map<String, Object>> getDefaultPrice(
      @PathVariable String stockTicker) {

    log.info("기본 가격 조회: stockTicker={}", stockTicker);

    Integer defaultPrice = stockPriceService.getDefaultPriceByTicker(stockTicker);

    return ResponseEntity.ok(Map.of(
        "stockTicker", stockTicker,
        "defaultPrice", defaultPrice,
        "note", "실시간 데이터가 없을 때 사용되는 기본 가격입니다",
        "timestamp", System.currentTimeMillis()
    ));
  }

  /**
   * 서비스 헬스체크
   */
  @GetMapping("/health")
  public ResponseEntity<Map<String, Object>> healthCheck() {

    log.info("주식 서비스 헬스체크");

    try {
      Map<String, Object> healthStatus = stockStatusService.healthCheck();
      String status = (String) healthStatus.get("status");

      return switch (status) {
        case "HEALTHY" -> ResponseEntity.ok(healthStatus);
        case "DEGRADED" -> ResponseEntity.status(206).body(healthStatus);
        default -> ResponseEntity.status(503).body(healthStatus);
      };

    } catch (Exception e) {
      log.error("헬스체크 실패", e);

      return ResponseEntity.status(500).body(Map.of(
          "status", "ERROR",
          "message", "헬스체크 실패: " + e.getMessage(),
          "timestamp", System.currentTimeMillis()
      ));
    }
  }
}