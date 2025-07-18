package com.project.stockpay.stock.service;

import com.project.stockpay.common.entity.Stock;
import com.project.stockpay.common.repository.StockRepository;
import com.project.stockpay.common.websocket.KisWebSocketClient;
import com.project.stockpay.common.websocket.dto.RealTimeOrderbookDto;
import com.project.stockpay.common.websocket.dto.RealTimeStockPriceDto;
import com.project.stockpay.stock.dto.StockPriceInfoDto;
import jakarta.transaction.Transactional;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 주식 가격 정보 조회 서비스
 * - 실시간 주가 데이터 조회
 * - 주식 목록 조회
 * - 장내/장외 시간 판단
 * - 종목 검색 기능
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockPriceService {

  private final StockRepository stockRepository;
  private final KisWebSocketClient webSocketClient;
  private final RedisTemplate<String, Object> redisTemplate;

  // 거래 시간 상수
  private static final LocalTime MARKET_OPEN = LocalTime.of(9, 0);
  private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 30);

  /**
   * 현재 시간 기준 장내/장외 판단
   */
  public boolean isMarketOpen() {
    LocalDateTime now = LocalDateTime.now();
    DayOfWeek dayOfWeek = now.getDayOfWeek();
    LocalTime currentTime = now.toLocalTime();

    // 주말은 장외시간
    if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
      return false;
    }

    // 평일 09:00 ~ 15:30만 장내시간
    return !currentTime.isBefore(MARKET_OPEN) && !currentTime.isAfter(MARKET_CLOSE);
  }

  /**
   * 장 상태 정보 조회
   */
  public Map<String, Object> getMarketStatus() {
    boolean isOpen = isMarketOpen();
    LocalDateTime now = LocalDateTime.now();

    Map<String, Object> status = new HashMap<>();
    status.put("isOpen", isOpen);
    status.put("currentTime", now.toString());
    status.put("marketOpenTime", MARKET_OPEN.toString());
    status.put("marketCloseTime", MARKET_CLOSE.toString());

    if (isOpen) {
      status.put("status", "OPEN");
      status.put("message", "장중 - 실시간 거래 가능");
    } else {
      status.put("status", "CLOSED");
      status.put("message", "장외시간 - 예약 주문만 가능");
    }

    return status;
  }

  /**
   * 실시간 주가 데이터 조회
   */
  public RealTimeStockPriceDto getRealTimeStockPrice(String stockTicker) {
    try {
      String cacheKey = "realtime:stock:" + stockTicker;
      RealTimeStockPriceDto priceData = (RealTimeStockPriceDto) redisTemplate.opsForValue().get(cacheKey);

      if (priceData != null) {
        log.debug("실시간 주가 조회 성공: {} = {}", stockTicker, priceData.getCurrentPrice());
        return priceData;
      } else {
        log.warn("실시간 주가 데이터 없음: {}", stockTicker);
        return null;
      }
    } catch (Exception e) {
      log.error("실시간 주가 조회 실패: {}", stockTicker, e);
      return null;
    }
  }

  /**
   * 실시간 호가 데이터 조회
   */
  public RealTimeOrderbookDto getRealTimeOrderbook(String stockTicker) {
    try {
      String cacheKey = "realtime:orderbook:" + stockTicker;
      RealTimeOrderbookDto orderbookData = (RealTimeOrderbookDto) redisTemplate.opsForValue().get(cacheKey);

      if (orderbookData != null) {
        log.debug("실시간 호가 조회 성공: {}", stockTicker);
        return orderbookData;
      } else {
        log.warn("실시간 호가 데이터 없음: {}", stockTicker);
        return null;
      }
    } catch (Exception e) {
      log.error("실시간 호가 조회 실패: {}", stockTicker, e);
      return null;
    }
  }

  /**
   * 종합 주가 정보 조회 (실시간 데이터 + 기본 정보)
   */
  public StockPriceInfoDto getStockPriceInfo(String stockTicker) {
    // 1. 기본 종목 정보 조회
    Stock stock = stockRepository.findById(stockTicker).orElse(null);
    if (stock == null) {
      log.warn("종목 정보를 찾을 수 없습니다: {}", stockTicker);
      return null;
    }

    // 2. 실시간 주가 데이터 조회
    RealTimeStockPriceDto realTimePrice = getRealTimeStockPrice(stockTicker);

    // 3. 실시간 호가 데이터 조회
    RealTimeOrderbookDto orderbook = getRealTimeOrderbook(stockTicker);

    // 4. 종합 정보 생성
    return StockPriceInfoDto.builder()
        .stockTicker(stock.getStockTicker())
        .stockName(stock.getStockName())
        .stockSector(stock.getStockSector())
        .stockStatus(stock.getStockStatus())
        .realTimePrice(realTimePrice)
        .orderbook(orderbook)
        .isMarketOpen(isMarketOpen())
        .timestamp(System.currentTimeMillis())
        .build();
  }

  /**
   * 여러 종목 주가 정보 조회
   */
  public List<StockPriceInfoDto> getMultipleStockPrices(List<String> stockTickers) {
    return stockTickers.stream()
        .map(this::getStockPriceInfo)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  /**
   * 주요 종목 주가 정보 조회
   */
  public List<StockPriceInfoDto> getPopularStockPrices() {
    List<String> popularStocks = Arrays.asList(
        "005930", // 삼성전자
        "000660", // SK하이닉스
        "035420", // NAVER
        "051910", // LG화학
        "006400", // 삼성SDI
        "207940", // 삼성바이오로직스
        "005380", // 현대차
        "012330", // 현대모비스
        "028260", // 삼성물산
        "066570"  // LG전자
    );

    return getMultipleStockPrices(popularStocks);
  }

  /**
   * 섹터별 주가 정보 조회
   */
  public List<StockPriceInfoDto> getStockPricesBySector(String sector) {
    List<Stock> stocks = stockRepository.findByStockSector(sector);
    List<String> stockTickers = stocks.stream()
        .map(Stock::getStockTicker)
        .collect(Collectors.toList());

    return getMultipleStockPrices(stockTickers);
  }

  /**
   * 종목 검색 (이름 또는 티커로)
   */
  public List<StockPriceInfoDto> searchStocks(String keyword) {
    List<Stock> stocks = stockRepository.findAll();

    List<Stock> filteredStocks = stocks.stream()
        .filter(stock ->
            stock.getStockName().contains(keyword) ||
                stock.getStockTicker().contains(keyword))
        .limit(20) // 최대 20개 결과
        .collect(Collectors.toList());

    List<String> stockTickers = filteredStocks.stream()
        .map(Stock::getStockTicker)
        .collect(Collectors.toList());

    return getMultipleStockPrices(stockTickers);
  }

  /**
   * 활성 종목 목록 조회 (실시간 데이터가 있는 종목들)
   */
  public List<String> getActiveStockList() {
    try {
      Set<String> stockKeys = redisTemplate.keys("realtime:stock:*");
      if (stockKeys != null) {
        return stockKeys.stream()
            .map(key -> key.replace("realtime:stock:", ""))
            .collect(Collectors.toList());
      }
      return new ArrayList<>();
    } catch (Exception e) {
      log.error("활성 종목 목록 조회 실패", e);
      return new ArrayList<>();
    }
  }

  /**
   * 실시간 데이터 통계 조회
   */
  public Map<String, Object> getRealTimeDataStats() {
    try {
      Set<String> stockKeys = redisTemplate.keys("realtime:stock:*");
      Set<String> orderbookKeys = redisTemplate.keys("realtime:orderbook:*");

      Map<String, Object> stats = new HashMap<>();
      stats.put("total_stocks", stockKeys != null ? stockKeys.size() : 0);
      stats.put("total_orderbooks", orderbookKeys != null ? orderbookKeys.size() : 0);
      stats.put("websocket_connected", webSocketClient.isConnected());
      stats.put("market_open", isMarketOpen());
      stats.put("timestamp", System.currentTimeMillis());

      return stats;
    } catch (Exception e) {
      log.error("실시간 데이터 통계 조회 실패", e);
      return new HashMap<>();
    }
  }

  /**
   * 종목 구독 요청
   */
  public boolean subscribeStock(String stockTicker) {
    try {
      // 1. 종목 정보 확인
      Stock stock = stockRepository.findById(stockTicker).orElse(null);
      if (stock == null) {
        log.warn("구독 요청 실패 - 종목 정보 없음: {}", stockTicker);
        return false;
      }

      // 2. 웹소켓 구독 요청
      webSocketClient.subscribeStockPrice(stockTicker);
      webSocketClient.subscribeStockOrderbook(stockTicker);

      log.info("종목 구독 요청 완료: {} ({})", stockTicker, stock.getStockName());
      return true;

    } catch (Exception e) {
      log.error("종목 구독 요청 실패: {}", stockTicker, e);
      return false;
    }
  }

  /**
   * 종목 구독 해제
   */
  public boolean unsubscribeStock(String stockTicker) {
    try {
      webSocketClient.unsubscribe("H0STCNT0", stockTicker); // 주식 체결가 구독 해제
      webSocketClient.unsubscribe("H0STASP0", stockTicker); // 주식 호가 구독 해제

      log.info("종목 구독 해제 완료: {}", stockTicker);
      return true;

    } catch (Exception e) {
      log.error("종목 구독 해제 실패: {}", stockTicker, e);
      return false;
    }
  }

  /**
   * 전체 종목 목록 조회
   */
  public List<Stock> getAllStocks() {
    return stockRepository.findAll();
  }

  /**
   * 상장 종목 목록 조회
   */
  public List<Stock> getListedStocks() {
    return stockRepository.findByStockStatus("LISTED");
  }

  /**
   * 현재가 조회 (장내/장외 시간 고려)
   */
  public Integer getCurrentPrice(String stockTicker) {
    if (isMarketOpen()) {
      // 장내 시간: 실시간 주가 사용
      RealTimeStockPriceDto realTimePrice = getRealTimeStockPrice(stockTicker);
      if (realTimePrice != null) {
        return realTimePrice.getCurrentPrice();
      }
    }

    // 장외 시간 또는 실시간 데이터 없음: 전일 종가 사용
    return getPreviousClosePrice(stockTicker);
  }

  /**
   * 전일 종가 조회 (임시 구현)
   */
  private Integer getPreviousClosePrice(String stockTicker) {
    try {
      // 실시간 데이터가 있으면 그것을 사용 (장외 시간의 전일 종가로 간주)
      RealTimeStockPriceDto realTimePrice = getRealTimeStockPrice(stockTicker);
      if (realTimePrice != null) {
        return realTimePrice.getCurrentPrice();
      }

      // 임시 기본값 (실제로는 DB에서 과거 데이터 조회해야 함)
      switch (stockTicker) {
        case "005930": return 70000; // 삼성전자
        case "035420": return 200000; // NAVER
        case "000660": return 120000; // SK하이닉스
        case "051910": return 300000; // LG화학
        case "006400": return 250000; // 삼성SDI
        default: return 50000; // 기본값
      }
    } catch (Exception e) {
      log.error("전일 종가 조회 실패: {}", stockTicker, e);
      return 50000; // 기본값
    }
  }

  /**
   * 캐시 갱신 (수동)
   */
  public void refreshCache(String stockTicker) {
    try {
      // 기존 캐시 삭제
      redisTemplate.delete("realtime:stock:" + stockTicker);
      redisTemplate.delete("realtime:orderbook:" + stockTicker);

      // 새로운 구독 요청
      subscribeStock(stockTicker);

      log.info("캐시 갱신 완료: {}", stockTicker);
    } catch (Exception e) {
      log.error("캐시 갱신 실패: {}", stockTicker, e);
    }
  }
}