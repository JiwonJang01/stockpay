package com.project.stockpay.stock.controller;

import com.project.stockpay.stock.dto.StockPriceInfoDto;
import com.project.stockpay.stock.service.StockPriceService;
import com.project.stockpay.common.websocket.dto.RealTimeStockPriceDto;
import com.project.stockpay.common.websocket.dto.RealTimeOrderbookDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 주가 조회 API 컨트롤러
 * - 실시간 주가 데이터 조회
 * - 종목 검색 및 목록 조회
 * - 시장 상태 조회
 * - 구독 관리
 */
@RestController
@RequestMapping("/stock/price")
@RequiredArgsConstructor
@Slf4j
public class StockPriceController {

  private final StockPriceService stockPriceService;

  /**
   * 시장 상태 조회 GET /stock/price/market-status
   */
  @GetMapping("/market-status")
  public ResponseEntity<Map<String, Object>> getMarketStatus() {
    try {
      Map<String, Object> marketStatus = stockPriceService.getMarketStatus();
      return ResponseEntity.ok(marketStatus);
    } catch (Exception e) {
      log.error("시장 상태 조회 실패", e);
      return ResponseEntity.internalServerError()
          .body(Map.of("error", "시장 상태 조회 실패: " + e.getMessage()));
    }
  }

  /**
   * 종목 주가 정보 조회 GET /stock/price/{stockTicker}
   */
  @GetMapping("/{stockTicker}")
  public ResponseEntity<StockPriceInfoDto> getStockPrice(@PathVariable String stockTicker) {
    try {
      log.info("종목 주가 정보 조회: {}", stockTicker);

      StockPriceInfoDto priceInfo = stockPriceService.getStockPriceInfo(stockTicker);

      if (priceInfo != null) {
        return ResponseEntity.ok(priceInfo);
      } else {
        return ResponseEntity.notFound().build();
      }
    } catch (Exception e) {
      log.error("종목 주가 정보 조회 실패: {}", stockTicker, e);
      return ResponseEntity.internalServerError().build();
    }
  }

  /**
   * 실시간 주가 데이터만 조회 GET /stock/price/{stockTicker}/realtime
   */
  @GetMapping("/{stockTicker}/realtime")
  public ResponseEntity<RealTimeStockPriceDto> getRealTimePrice(@PathVariable String stockTicker) {
    try {
      log.info("실시간 주가 데이터 조회: {}", stockTicker);

      RealTimeStockPriceDto realTimePrice = stockPriceService.getRealTimeStockPrice(stockTicker);

      if (realTimePrice != null) {
        return ResponseEntity.ok(realTimePrice);
      } else {
        return ResponseEntity.notFound().build();
      }
    } catch (Exception e) {
      log.error("실시간 주가 데이터 조회 실패: {}", stockTicker, e);
      return ResponseEntity.internalServerError().build();
    }
  }

  /**
   * 실시간 호가 데이터 조회 GET /stock/price/{stockTicker}/orderbook
   */
  @GetMapping("/{stockTicker}/orderbook")
  public ResponseEntity<RealTimeOrderbookDto> getOrderbook(@PathVariable String stockTicker) {
    try {
      log.info("실시간 호가 데이터 조회: {}", stockTicker);

      RealTimeOrderbookDto orderbook = stockPriceService.getRealTimeOrderbook(stockTicker);

      if (orderbook != null) {
        return ResponseEntity.ok(orderbook);
      } else {
        return ResponseEntity.notFound().build();
      }
    } catch (Exception e) {
      log.error("실시간 호가 데이터 조회 실패: {}", stockTicker, e);
      return ResponseEntity.internalServerError().build();
    }
  }

  /**
   * 여러 종목 주가 정보 조회 POST /stock/price/multiple
   */
  @PostMapping("/multiple")
  public ResponseEntity<List<StockPriceInfoDto>> getMultipleStockPrices(
      @RequestBody List<String> stockTickers) {
    try {
      log.info("여러 종목 주가 정보 조회: {}", stockTickers);

      List<StockPriceInfoDto> priceInfos = stockPriceService.getMultipleStockPrices(stockTickers);
      return ResponseEntity.ok(priceInfos);
    } catch (Exception e) {
      log.error("여러 종목 주가 정보 조회 실패", e);
      return ResponseEntity.internalServerError().build();
    }
  }

  /**
   * 주요 종목 주가 정보 조회 GET /stock/price/popular
   */
  @GetMapping("/popular")
  public ResponseEntity<List<StockPriceInfoDto>> getPopularStockPrices() {
    try {
      log.info("주요 종목 주가 정보 조회");

      List<StockPriceInfoDto> priceInfos = stockPriceService.getPopularStockPrices();
      return ResponseEntity.ok(priceInfos);
    } catch (Exception e) {
      log.error("주요 종목 주가 정보 조회 실패", e);
      return ResponseEntity.internalServerError().build();
    }
  }

  /**
   * 섹터별 주가 정보 조회 GET /stock/price/sector/{sector}
   */
  @GetMapping("/sector/{sector}")
  public ResponseEntity<List<StockPriceInfoDto>> getStockPricesBySector(
      @PathVariable String sector) {
    try {
      log.info("섹터별 주가 정보 조회: {}", sector);

      List<StockPriceInfoDto> priceInfos = stockPriceService.getStockPricesBySector(sector);
      return ResponseEntity.ok(priceInfos);
    } catch (Exception e) {
      log.error("섹터별 주가 정보 조회 실패: {}", sector, e);
      return ResponseEntity.internalServerError().build();
    }
  }

  /**
   * 종목 검색 GET /stock/price/search?keyword={keyword}
   */
  @GetMapping("/search")
  public ResponseEntity<List<StockPriceInfoDto>> searchStocks(@RequestParam String keyword) {
    try {
      log.info("종목 검색: {}", keyword);

      if (keyword == null || keyword.trim().isEmpty()) {
        return ResponseEntity.badRequest().build();
      }

      List<StockPriceInfoDto> priceInfos = stockPriceService.searchStocks(keyword.trim());
      return ResponseEntity.ok(priceInfos);
    } catch (Exception e) {
      log.error("종목 검색 실패: {}", keyword, e);
      return ResponseEntity.internalServerError().build();
    }
  }

  /**
   * 활성 종목 목록 조회 (실시간 데이터가 있는 종목들) GET /stock/price/active
   */
  @GetMapping("/active")
  public ResponseEntity<Map<String, Object>> getActiveStocks() {
    try {
      log.info("활성 종목 목록 조회");

      List<String> activeStocks = stockPriceService.getActiveStockList();
      Map<String, Object> response = new HashMap<>();
      response.put("activeStocks", activeStocks);
      response.put("count", activeStocks.size());
      response.put("timestamp", System.currentTimeMillis());

      return ResponseEntity.ok(response);
    } catch (Exception e) {
      log.error("활성 종목 목록 조회 실패", e);
      return ResponseEntity.internalServerError().build();
    }
  }

  /**
   * 실시간 데이터 통계 조회 GET /stock/price/stats
   */
  @GetMapping("/stats")
  public ResponseEntity<Map<String, Object>> getRealTimeDataStats() {
    try {
      log.info("실시간 데이터 통계 조회");

      Map<String, Object> stats = stockPriceService.getRealTimeDataStats();
      return ResponseEntity.ok(stats);
    } catch (Exception e) {
      log.error("실시간 데이터 통계 조회 실패", e);
      return ResponseEntity.internalServerError().build();
    }
  }

  /**
   * 종목 구독 요청 POST /stock/price/{stockTicker}/subscribe
   */
  @PostMapping("/{stockTicker}/subscribe")
  public ResponseEntity<Map<String, Object>> subscribeStock(@PathVariable String stockTicker) {
    try {
      log.info("종목 구독 요청: {}", stockTicker);

      boolean success = stockPriceService.subscribeStock(stockTicker);

      Map<String, Object> response = new HashMap<>();
      response.put("success", success);
      response.put("stockTicker", stockTicker);
      response.put("message", success ? "구독 요청 성공" : "구독 요청 실패");
      response.put("timestamp", System.currentTimeMillis());

      return ResponseEntity.ok(response);
    } catch (Exception e) {
      log.error("종목 구독 요청 실패: {}", stockTicker, e);
      return ResponseEntity.internalServerError()
          .body(Map.of("success", false, "message", "구독 요청 실패: " + e.getMessage()));
    }
  }

  /**
   * 종목 구독 해제 DELETE /stock/price/{stockTicker}/subscribe
   */
  @DeleteMapping("/{stockTicker}/subscribe")
  public ResponseEntity<Map<String, Object>> unsubscribeStock(@PathVariable String stockTicker) {
    try {
      log.info("종목 구독 해제: {}", stockTicker);

      boolean success = stockPriceService.unsubscribeStock(stockTicker);

      Map<String, Object> response = new HashMap<>();
      response.put("success", success);
      response.put("stockTicker", stockTicker);
      response.put("message", success ? "구독 해제 성공" : "구독 해제 실패");
      response.put("timestamp", System.currentTimeMillis());

      return ResponseEntity.ok(response);
    } catch (Exception e) {
      log.error("종목 구독 해제 실패: {}", stockTicker, e);
      return ResponseEntity.internalServerError()
          .body(Map.of("success", false, "message", "구독 해제 실패: " + e.getMessage()));
    }
  }

  /**
   * 현재가 조회 (간단한 가격만) GET /stock/price/{stockTicker}/current
   */
  @GetMapping("/{stockTicker}/current")
  public ResponseEntity<Map<String, Object>> getCurrentPrice(@PathVariable String stockTicker) {
    try {
      log.info("현재가 조회: {}", stockTicker);

      Integer currentPrice = stockPriceService.getCurrentPrice(stockTicker);

      Map<String, Object> response = new HashMap<>();
      response.put("stockTicker", stockTicker);
      response.put("currentPrice", currentPrice);
      response.put("isMarketOpen", stockPriceService.isMarketOpen());
      response.put("timestamp", System.currentTimeMillis());

      return ResponseEntity.ok(response);
    } catch (Exception e) {
      log.error("현재가 조회 실패: {}", stockTicker, e);
      return ResponseEntity.internalServerError().build();
    }
  }

  /**
   * 캐시 갱신 (수동) POST /stock/price/{stockTicker}/refresh
   */
  @PostMapping("/{stockTicker}/refresh")
  public ResponseEntity<Map<String, Object>> refreshCache(@PathVariable String stockTicker) {
    try {
      log.info("캐시 갱신 요청: {}", stockTicker);

      stockPriceService.refreshCache(stockTicker);

      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      response.put("stockTicker", stockTicker);
      response.put("message", "캐시 갱신 완료");
      response.put("timestamp", System.currentTimeMillis());

      return ResponseEntity.ok(response);
    } catch (Exception e) {
      log.error("캐시 갱신 실패: {}", stockTicker, e);
      return ResponseEntity.internalServerError()
          .body(Map.of("success", false, "message", "캐시 갱신 실패: " + e.getMessage()));
    }
  }

  /**
   * 거래 가능한 종목 목록 조회 GET /stock/price/tradable
   */
  @GetMapping("/tradable")
  public ResponseEntity<List<StockPriceInfoDto>> getTradableStocks() {
    try {
      log.info("거래 가능한 종목 목록 조회");

      List<StockPriceInfoDto> popularStocks = stockPriceService.getPopularStockPrices();
      List<StockPriceInfoDto> tradableStocks = popularStocks.stream()
          .filter(StockPriceInfoDto::isTradable)
          .toList();

      return ResponseEntity.ok(tradableStocks);
    } catch (Exception e) {
      log.error("거래 가능한 종목 목록 조회 실패", e);
      return ResponseEntity.internalServerError().build();
    }
  }
}
