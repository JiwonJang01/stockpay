package com.project.stockpay.stock.service;

import com.project.stockpay.common.entity.Stock;
import com.project.stockpay.common.repository.StockRepository;
import com.project.stockpay.common.websocket.dto.RealTimeOrderbookDto;
import com.project.stockpay.common.websocket.dto.RealTimeStockPriceDto;
import com.project.stockpay.stock.dto.StockPriceInfoDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 주식 가격 정보 조회 서비스
 * - 실시간 주가/호가 데이터 조회
 * - 현재가 및 전일 종가 조회
 * - 종목 정보 및 검색 기능
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockPriceService {

  private final StockRepository stockRepository;
  private final RedisTemplate<String, Object> redisTemplate;
  private final StockStatusService stockStatusService;
  private final StockUtilService stockUtilService;

  // ========== 실시간 데이터 조회 ==========

  /**
   * 실시간 주가 데이터 조회
   */
  public RealTimeStockPriceDto getRealTimeStockPrice(String stockTicker) {
    if (stockTicker == null || stockTicker.trim().isEmpty()) {
      log.warn("주가 조회 실패 - 종목 코드 없음");
      return null;
    }

    try {
      String trimmedTicker = stockTicker.trim();
      String cacheKey = "realtime:stock:" + trimmedTicker;
      RealTimeStockPriceDto priceData = (RealTimeStockPriceDto) redisTemplate.opsForValue()
          .get(cacheKey);

      if (priceData != null) {
        // 데이터 신선도 체크
        if (stockStatusService.isDataFresh(priceData)) {
          log.debug("실시간 주가 조회 성공 (신선): {} = {}", trimmedTicker, priceData.getCurrentPrice());
          return priceData;
        } else {
          log.debug("실시간 주가 데이터 오래됨: {} ({}분 전)", trimmedTicker,
              stockStatusService.getDataAgeInMinutes(priceData));

          // 장외시간에는 오래된 데이터도 유용할 수 있으므로 반환
          if (!stockStatusService.isMarketOpen()) {
            return priceData;
          }
          return null; // 장중에는 신선한 데이터만 사용
        }
      } else {
        log.debug("실시간 주가 데이터 없음: {}", trimmedTicker);
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
    if (stockTicker == null || stockTicker.trim().isEmpty()) {
      log.warn("호가 조회 실패 - 종목 코드 없음");
      return null;
    }

    try {
      String trimmedTicker = stockTicker.trim();
      String cacheKey = "realtime:orderbook:" + trimmedTicker;
      RealTimeOrderbookDto orderbookData = (RealTimeOrderbookDto) redisTemplate.opsForValue()
          .get(cacheKey);

      if (orderbookData != null) {
        // 호가 데이터 신선도 체크 (5분 이내)
        long dataAge = System.currentTimeMillis() - orderbookData.getTimestamp();
        if (dataAge < 300_000L) {
          log.debug("실시간 호가 조회 성공 (신선): {}", trimmedTicker);
          return orderbookData;
        } else {
          log.debug("실시간 호가 데이터 오래됨: {} ({}분 전)", trimmedTicker, dataAge / 60000);

          // 장외시간에는 오래된 데이터도 반환
          if (!stockStatusService.isMarketOpen()) {
            return orderbookData;
          }
          return null; // 장중에는 신선한 데이터만 사용
        }
      } else {
        log.debug("실시간 호가 데이터 없음: {}", trimmedTicker);
        return null;
      }
    } catch (Exception e) {
      log.error("실시간 호가 조회 실패: {}", stockTicker, e);
      return null;
    }
  }

  // ========== 현재가 조회 (핵심 메서드) ==========

  /**
   * 현재가 조회 (장내/장외 시간 고려)
   * TradingService에서 사용하는 핵심 메서드
   */
  public Integer getCurrentPrice(String stockTicker) {
    if (stockTicker == null || stockTicker.trim().isEmpty()) {
      log.warn("현재가 조회 실패 - 종목 코드 없음");
      return null;
    }

    String trimmedTicker = stockTicker.trim();

    if (stockStatusService.isMarketOpen()) {
      // 장내 시간: 실시간 주가 우선 사용
      RealTimeStockPriceDto realTimePrice = getRealTimeStockPrice(trimmedTicker);
      if (realTimePrice != null && stockStatusService.isDataFresh(realTimePrice)) {
        log.debug("장내시간 - 실시간 주가 사용: {} = {}", trimmedTicker, realTimePrice.getCurrentPrice());
        return realTimePrice.getCurrentPrice();
      } else {
        log.debug("장내시간이지만 신선한 실시간 데이터 없음 - 전일 종가 사용: {}", trimmedTicker);
      }
    } else {
      log.debug("장외시간 - 전일 종가 사용: {}", trimmedTicker);
    }

    // 장외 시간 또는 신선한 실시간 데이터 없음: 전일 종가 사용
    return getPreviousClosePrice(trimmedTicker);
  }

  /**
   * 전일 종가 조회 (다층 캐싱 전략)
   */
  public Integer getPreviousClosePrice(String stockTicker) {
    try {
      String trimmedTicker = stockTicker.trim();

      // 1. 캐시된 실시간 데이터가 있으면 전일 종가로 간주 (임시)
      String realtimeCacheKey = "realtime:stock:" + trimmedTicker;
      RealTimeStockPriceDto cachedPrice = (RealTimeStockPriceDto) redisTemplate.opsForValue()
          .get(realtimeCacheKey);

      if (cachedPrice != null) {
        log.debug("캐시된 실시간 주가를 전일 종가로 사용: {} = {}", trimmedTicker, cachedPrice.getCurrentPrice());
        return cachedPrice.getCurrentPrice();
      }

      // 2. 전일 종가 전용 캐시 확인
      String closePriceKey = "close:stock:" + trimmedTicker;
      Integer closePrice = (Integer) redisTemplate.opsForValue().get(closePriceKey);
      if (closePrice != null) {
        log.debug("전일 종가 캐시 사용: {} = {}", trimmedTicker, closePrice);
        return closePrice;
      }

      // 3. 종목별 기본 가격 사용
      Integer defaultPrice = getDefaultPriceByTicker(trimmedTicker);
      log.debug("기본 가격 사용: {} = {}", trimmedTicker, defaultPrice);
      return defaultPrice;

    } catch (Exception e) {
      log.error("전일 종가 조회 실패: {}", stockTicker, e);
      return getDefaultPriceByTicker(stockTicker);
    }
  }

  /**
   * 종목별 기본 가격 조회
   * TODO: 추후 DB 테이블로 이동 검토 필요
   */
  public Integer getDefaultPriceByTicker(String stockTicker) {
    if (stockTicker == null || stockTicker.trim().isEmpty()) {
      return 50000; // 기본값
    }

    return switch (stockTicker.trim()) {
      // 대형주
      case "005930" -> 70000;   // 삼성전자
      case "000660" -> 120000;  // SK하이닉스
      case "207940" -> 800000;  // 삼성바이오로직스
      case "005380" -> 180000;  // 현대차
      case "051910" -> 300000;  // LG화학
      case "006400" -> 250000;  // 삼성SDI

      // IT/플랫폼
      case "035420" -> 200000;  // NAVER
      case "035720" -> 90000;   // 카카오

      // 중견주
      case "012330" -> 250000;  // 현대모비스
      case "028260" -> 120000;  // 삼성물산
      case "066570" -> 130000;  // LG전자
      case "373220" -> 90000;   // LG에너지솔루션
      case "003670" -> 35000;   // 포스코홀딩스

      // 통신/에너지
      case "096770" -> 600000;  // SK이노베이션
      case "017670" -> 45000;   // SK텔레콤
      case "030200" -> 25000;   // KT

      // 금융
      case "055550" -> 65000;   // 신한지주
      case "105560" -> 60000;   // KB금융

      // 기타
      case "000270" -> 25000;   // 기아
      case "009150" -> 85000;   // 삼성전기

      default -> 50000;         // 기본값
    };
  }

  // ========== 종목 정보 조회 ==========

  /**
   * 종합 주가 정보 조회 (실시간 데이터 + 기본 정보)
   */
  public StockPriceInfoDto getStockPriceInfo(String stockTicker) {
    if (stockTicker == null || stockTicker.trim().isEmpty()) {
      log.warn("종목 정보 조회 실패 - 종목 코드 없음");
      return null;
    }

    String trimmedTicker = stockTicker.trim();

    // 1. 기본 종목 정보 조회
    Stock stock = stockRepository.findById(trimmedTicker).orElse(null);
    if (stock == null) {
      log.warn("종목 정보를 찾을 수 없습니다: {}", trimmedTicker);
      return null;
    }

    // 2. 실시간 주가 데이터 조회
    RealTimeStockPriceDto realTimePrice = getRealTimeStockPrice(trimmedTicker);

    // 3. 실시간 호가 데이터 조회
    RealTimeOrderbookDto orderbook = getRealTimeOrderbook(trimmedTicker);

    // 4. 종합 정보 생성
    return StockPriceInfoDto.builder()
        .stockTicker(stock.getStockTicker())
        .stockName(stock.getStockName())
        .stockSector(stock.getStockSector())
        .stockStatus(stock.getStockStatus())
        .realTimePrice(realTimePrice)
        .orderbook(orderbook)
        .isMarketOpen(stockStatusService.isMarketOpen())
        .timestamp(System.currentTimeMillis())
        .build();
  }

  /**
   * 여러 종목 주가 정보 조회 (최적화됨)
   */
  public List<StockPriceInfoDto> getMultipleStockPrices(List<String> stockTickers) {
    if (stockTickers == null || stockTickers.isEmpty()) {
      return new ArrayList<>();
    }

    return stockTickers.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(ticker -> !ticker.isEmpty())
        .distinct() // 중복 제거
        .limit(100) // 최대 100개로 제한
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
    if (sector == null || sector.trim().isEmpty()) {
      return new ArrayList<>();
    }

    try {
      List<Stock> stocks = stockRepository.findByStockSector(sector.trim());
      List<String> stockTickers = stocks.stream()
          .map(Stock::getStockTicker)
          .limit(50) // 최대 50개로 제한
          .collect(Collectors.toList());

      return getMultipleStockPrices(stockTickers);
    } catch (Exception e) {
      log.error("섹터별 주가 정보 조회 실패: {}", sector, e);
      return new ArrayList<>();
    }
  }

  /**
   * 종목 검색 (이름 또는 티커로)
   */
  public List<StockPriceInfoDto> searchStocks(String keyword) {
    if (keyword == null || keyword.trim().isEmpty()) {
      return new ArrayList<>();
    }

    try {
      String searchKeyword = keyword.trim().toLowerCase();
      List<Stock> stocks = stockRepository.findAll();

      List<Stock> filteredStocks = stocks.stream()
          .filter(stock ->
              stock.getStockName().toLowerCase().contains(searchKeyword) ||
                  stock.getStockTicker().toLowerCase().contains(searchKeyword))
          .limit(20) // 최대 20개 결과
          .collect(Collectors.toList());

      List<String> stockTickers = filteredStocks.stream()
          .map(Stock::getStockTicker)
          .collect(Collectors.toList());

      return getMultipleStockPrices(stockTickers);
    } catch (Exception e) {
      log.error("종목 검색 실패: {}", keyword, e);
      return new ArrayList<>();
    }
  }

  // ========== 기본 정보 조회 ==========

  /**
   * 전체 종목 목록 조회
   */
  public List<Stock> getAllStocks() {
    try {
      return stockRepository.findAll();
    } catch (Exception e) {
      log.error("전체 종목 목록 조회 실패", e);
      return new ArrayList<>();
    }
  }

  /**
   * 상장 종목 목록 조회
   */
  public List<Stock> getListedStocks() {
    try {
      return stockRepository.findByStockStatus("LISTED");
    } catch (Exception e) {
      log.error("상장 종목 목록 조회 실패", e);
      return new ArrayList<>();
    }
  }

  /**
   * 활성 종목 목록 조회 (실시간 데이터가 있는 종목들)
   */
  public List<String> getActiveStockList() {
    try {
      Set<String> stockKeys = redisTemplate.keys("realtime:stock:*");
      if (stockKeys != null && !stockKeys.isEmpty()) {
        return stockKeys.stream()
            .map(key -> key.replace("realtime:stock:", ""))
            .sorted() // 정렬
            .collect(Collectors.toList());
      }
      return new ArrayList<>();
    } catch (Exception e) {
      log.error("활성 종목 목록 조회 실패", e);
      return new ArrayList<>();
    }
  }
}