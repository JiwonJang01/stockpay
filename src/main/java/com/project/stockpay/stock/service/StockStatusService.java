package com.project.stockpay.stock.service;

import com.project.stockpay.common.entity.Stock;
import com.project.stockpay.common.repository.StockRepository;
import com.project.stockpay.common.websocket.KisWebSocketClient;
import com.project.stockpay.common.websocket.dto.RealTimeStockPriceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

/**
 * 주식 상태 관리 서비스
 * - 장 상태 관리 (개장/휴장)
 * - 데이터 신선도 체크
 * - 구독 관리
 * - 전일 종가 관리
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockStatusService {

  private final StockRepository stockRepository;
  private final KisWebSocketClient webSocketClient;
  private final RedisTemplate<String, Object> redisTemplate;

  // 거래 시간 상수
  private static final LocalTime MARKET_OPEN = LocalTime.of(9, 0);
  private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 30);

  // 데이터 신선도 기준 (5분)
  private static final long FRESH_DATA_THRESHOLD_MS = 300_000L;

  // ========== 시장 상태 관리 ==========

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
   * 장 상태 정보 조회 (확장된 정보)
   */
  public Map<String, Object> getMarketStatus() {
    boolean isOpen = isMarketOpen();
    LocalDateTime now = LocalDateTime.now();

    Map<String, Object> status = new HashMap<>();
    status.put("isOpen", isOpen);
    status.put("currentTime", now.toString());
    status.put("marketOpenTime", MARKET_OPEN.toString());
    status.put("marketCloseTime", MARKET_CLOSE.toString());
    status.put("timezone", "Asia/Seoul");

    if (isOpen) {
      status.put("status", "OPEN");
      status.put("message", "장중 - 실시간 거래 가능");

      // 장 마감까지 남은 시간 계산
      LocalTime closeTime = MARKET_CLOSE;
      Duration timeToClose = Duration.between(now.toLocalTime(), closeTime);
      if (!timeToClose.isNegative()) {
        status.put("timeToClose", String.format("%d시간 %d분",
            timeToClose.toHours(), timeToClose.toMinutesPart()));
        status.put("remainingMinutes", timeToClose.toMinutes());
      }
    } else {
      status.put("status", "CLOSED");
      status.put("message", "장외시간 - 예약 주문만 가능");

      // 다음 개장까지 남은 시간 계산
      LocalDateTime nextOpen = getNextMarketOpenTime(now);
      Duration timeToOpen = Duration.between(now, nextOpen);
      status.put("timeToOpen", String.format("%d일 %d시간 %d분",
          timeToOpen.toDays(), timeToOpen.toHoursPart(), timeToOpen.toMinutesPart()));
      status.put("nextOpenTime", nextOpen.toString());
    }

    // 실시간 데이터 통계
    var dataStats = getRealTimeDataStats();
    status.put("dataStats", dataStats);

    return status;
  }

  /**
   * 다음 개장 시간 계산
   */
  public LocalDateTime getNextMarketOpenTime(LocalDateTime now) {
    LocalDateTime nextOpen = now.toLocalDate().atTime(MARKET_OPEN);

    // 오늘 개장 시간이 지났으면 다음 영업일로
    if (now.toLocalTime().isAfter(MARKET_CLOSE) || now.toLocalTime().equals(MARKET_CLOSE)) {
      nextOpen = nextOpen.plusDays(1);
    }

    // 주말이면 월요일로
    while (nextOpen.getDayOfWeek() == DayOfWeek.SATURDAY ||
        nextOpen.getDayOfWeek() == DayOfWeek.SUNDAY) {
      nextOpen = nextOpen.plusDays(1);
    }

    return nextOpen;
  }

  // ========== 데이터 신선도 관리 ==========

  /**
   * 데이터 신선도 확인 (5분 이내)
   */
  public boolean isDataFresh(RealTimeStockPriceDto priceData) {
    if (priceData == null || priceData.getTimestamp() == null) {
      return false;
    }

    long dataAge = System.currentTimeMillis() - priceData.getTimestamp();
    return dataAge < FRESH_DATA_THRESHOLD_MS;
  }

  /**
   * 데이터 나이 계산 (분 단위)
   */
  public long getDataAgeInMinutes(RealTimeStockPriceDto priceData) {
    if (priceData == null || priceData.getTimestamp() == null) {
      return Long.MAX_VALUE;
    }

    long dataAge = System.currentTimeMillis() - priceData.getTimestamp();
    return dataAge / 60000; // 밀리초 → 분
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

      // 신선한 데이터 개수 계산
      if (stockKeys != null) {
        long freshDataCount = stockKeys.stream()
            .mapToLong(key -> {
              try {
                RealTimeStockPriceDto data = (RealTimeStockPriceDto) redisTemplate.opsForValue().get(key);
                return (data != null && isDataFresh(data)) ? 1 : 0;
              } catch (Exception e) {
                return 0;
              }
            })
            .sum();
        stats.put("fresh_data_count", freshDataCount);

        double freshRatio = stockKeys.size() > 0 ? (double) freshDataCount / stockKeys.size() * 100 : 0;
        stats.put("fresh_data_ratio", String.format("%.1f%%", freshRatio));
      } else {
        stats.put("fresh_data_count", 0);
        stats.put("fresh_data_ratio", "0.0%");
      }

      return stats;
    } catch (Exception e) {
      log.error("실시간 데이터 통계 조회 실패", e);
      return Map.of(
          "total_stocks", 0,
          "total_orderbooks", 0,
          "websocket_connected", false,
          "market_open", false,
          "fresh_data_count", 0,
          "fresh_data_ratio", "0.0%",
          "error", e.getMessage(),
          "timestamp", System.currentTimeMillis()
      );
    }
  }

  // ========== 구독 관리 ==========

  /**
   * 종목 구독 요청
   */
  public boolean subscribeStock(String stockTicker) {
    if (stockTicker == null || stockTicker.trim().isEmpty()) {
      log.warn("구독 요청 실패 - 종목 코드 없음");
      return false;
    }

    try {
      String trimmedTicker = stockTicker.trim();

      // 1. 종목 정보 확인
      Stock stock = stockRepository.findById(trimmedTicker).orElse(null);
      if (stock == null) {
        log.warn("구독 요청 실패 - 종목 정보 없음: {}", trimmedTicker);
        return false;
      }

      // 2. 웹소켓 연결 상태 확인
      if (!webSocketClient.isConnected()) {
        log.warn("구독 요청 실패 - 웹소켓 연결 안됨: {}", trimmedTicker);
        return false;
      }

      // 3. 웹소켓 구독 요청
      webSocketClient.subscribeStockPrice(trimmedTicker);
      webSocketClient.subscribeStockOrderbook(trimmedTicker);

      log.info("종목 구독 요청 완료: {} ({})", trimmedTicker, stock.getStockName());
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
    if (stockTicker == null || stockTicker.trim().isEmpty()) {
      log.warn("구독 해제 실패 - 종목 코드 없음");
      return false;
    }

    try {
      String trimmedTicker = stockTicker.trim();

      // 웹소켓 연결 상태 확인
      if (!webSocketClient.isConnected()) {
        log.warn("구독 해제 실패 - 웹소켓 연결 안됨: {}", trimmedTicker);
        return false;
      }

      webSocketClient.unsubscribe("H0STCNT0", trimmedTicker); // 주식 체결가 구독 해제
      webSocketClient.unsubscribe("H0STASP0", trimmedTicker); // 주식 호가 구독 해제

      log.info("종목 구독 해제 완료: {}", trimmedTicker);
      return true;

    } catch (Exception e) {
      log.error("종목 구독 해제 실패: {}", stockTicker, e);
      return false;
    }
  }

  // ========== 전일 종가 관리 ==========

  /**
   * 전일 종가 저장 (장 마감 시 호출)
   */
  public void saveClosePrices() {
    log.info("전일 종가 저장 시작");

    try {
      Set<String> stockKeys = redisTemplate.keys("realtime:stock:*");
      if (stockKeys == null || stockKeys.isEmpty()) {
        log.warn("저장할 실시간 데이터가 없음");
        return;
      }

      int savedCount = 0;
      for (String stockKey : stockKeys) {
        try {
          RealTimeStockPriceDto priceData = (RealTimeStockPriceDto) redisTemplate.opsForValue().get(stockKey);
          if (priceData != null) {
            String stockTicker = stockKey.replace("realtime:stock:", "");
            String closePriceKey = "close:stock:" + stockTicker;

            // 전일 종가로 저장 (7일간 보관)
            redisTemplate.opsForValue().set(closePriceKey, priceData.getCurrentPrice(),
                Duration.ofDays(7));
            savedCount++;
          }
        } catch (Exception e) {
          log.error("전일 종가 저장 실패: {}", stockKey, e);
        }
      }

      log.info("전일 종가 저장 완료: {}개 종목", savedCount);

    } catch (Exception e) {
      log.error("전일 종가 저장 실패", e);
    }
  }

  /**
   * 전일 종가 수동 설정 (테스트용)
   */
  public void setClosePrice(String stockTicker, Integer closePrice) {
    if (stockTicker == null || stockTicker.trim().isEmpty() || closePrice == null || closePrice <= 0) {
      log.warn("전일 종가 설정 실패 - 잘못된 입력: stockTicker={}, closePrice={}", stockTicker, closePrice);
      return;
    }

    try {
      String trimmedTicker = stockTicker.trim();
      String closePriceKey = "close:stock:" + trimmedTicker;

      redisTemplate.opsForValue().set(closePriceKey, closePrice, Duration.ofDays(7));
      log.info("전일 종가 설정 완료: {} = {}", trimmedTicker, closePrice);
    } catch (Exception e) {
      log.error("전일 종가 설정 실패: {} = {}", stockTicker, closePrice, e);
    }
  }

  /**
   * 전일 종가 일괄 설정 (테스트용)
   */
  public void setClosePricesForTesting() {
    log.info("테스트용 전일 종가 일괄 설정 시작");

    Map<String, Integer> testClosePrices = Map.of(
        "005930", 70000,   // 삼성전자
        "000660", 120000,  // SK하이닉스
        "035420", 200000,  // NAVER
        "051910", 300000,  // LG화학
        "006400", 250000,  // 삼성SDI
        "207940", 800000,  // 삼성바이오로직스
        "005380", 180000,  // 현대차
        "012330", 250000,  // 현대모비스
        "028260", 120000,  // 삼성물산
        "066570", 130000   // LG전자
    );

    int setCount = 0;
    for (Map.Entry<String, Integer> entry : testClosePrices.entrySet()) {
      try {
        setClosePrice(entry.getKey(), entry.getValue());
        setCount++;
      } catch (Exception e) {
        log.error("테스트 전일 종가 설정 실패: {} = {}", entry.getKey(), entry.getValue(), e);
      }
    }

    log.info("테스트용 전일 종가 일괄 설정 완료: {}개 종목", setCount);
  }

  // ========== 데이터 품질 관리 ==========

  /**
   * 데이터 품질 체크
   */
  public Map<String, Object> checkDataQuality() {
    try {
      Set<String> stockKeys = redisTemplate.keys("realtime:stock:*");

      if (stockKeys == null || stockKeys.isEmpty()) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "NO_DATA");
        result.put("message", "실시간 데이터가 없습니다");
        result.put("recommendation", "주요 종목 구독을 시작하세요");
        result.put("timestamp", System.currentTimeMillis());
        return result;
      }

      int totalCount = stockKeys.size();
      int freshCount = 0;
      int staleCount = 0;
      int errorCount = 0;

      for (String stockKey : stockKeys) {
        try {
          RealTimeStockPriceDto priceData = (RealTimeStockPriceDto) redisTemplate.opsForValue().get(stockKey);
          if (priceData != null) {
            if (isDataFresh(priceData)) {
              freshCount++;
            } else {
              staleCount++;
            }
          } else {
            errorCount++;
          }
        } catch (Exception e) {
          errorCount++;
        }
      }

      double freshRatio = (double) freshCount / totalCount * 100;
      String status;
      String message;

      if (freshRatio >= 80) {
        status = "EXCELLENT";
        message = "데이터 품질 우수";
      } else if (freshRatio >= 60) {
        status = "GOOD";
        message = "데이터 품질 양호";
      } else if (freshRatio >= 40) {
        status = "FAIR";
        message = "데이터 품질 보통";
      } else if (freshRatio >= 20) {
        status = "POOR";
        message = "데이터 품질 불량";
      } else {
        status = "CRITICAL";
        message = "데이터 품질 심각";
      }

      // HashMap으로 변경
      Map<String, Object> result = new HashMap<>();
      result.put("status", status);
      result.put("message", message);
      result.put("totalStocks", totalCount);
      result.put("freshData", freshCount);
      result.put("staleData", staleCount);
      result.put("errorData", errorCount);
      result.put("freshRatio", String.format("%.1f%%", freshRatio));
      result.put("websocketConnected", webSocketClient.isConnected());
      result.put("marketOpen", isMarketOpen());
      result.put("timestamp", System.currentTimeMillis());
      result.put("recommendation", getDataQualityRecommendation(status, freshRatio));

      return result;

    } catch (Exception e) {
      log.error("데이터 품질 체크 실패", e);

      Map<String, Object> errorResult = new HashMap<>();
      errorResult.put("status", "ERROR");
      errorResult.put("message", "데이터 품질 체크 중 오류 발생: " + e.getMessage());
      errorResult.put("recommendation", "시스템 관리자에게 문의하세요");
      errorResult.put("timestamp", System.currentTimeMillis());

      return errorResult;
    }
  }

  /**
   * 데이터 품질 개선 권고사항
   */
  private String getDataQualityRecommendation(String status, double freshRatio) {
    return switch (status) {
      case "EXCELLENT" -> "현재 데이터 품질이 우수합니다. 현재 상태를 유지하세요.";
      case "GOOD" -> "데이터 품질이 양호합니다. 정기적인 모니터링을 권장합니다.";
      case "FAIR" -> "일부 데이터가 오래되었습니다. 웹소켓 연결 상태를 확인하세요.";
      case "POOR" -> "데이터 품질이 불량합니다. 캐시 갱신 또는 웹소켓 재연결을 시도하세요.";
      case "CRITICAL" -> "데이터 품질이 심각합니다. 즉시 시스템 점검이 필요합니다.";
      default -> "웹소켓 연결을 확인하고 주요 종목 구독을 갱신하세요.";
    };
  }

  // ========== 헬스체크 ==========

  /**
   * 시장 상태 헬스체크
   */
  public Map<String, Object> healthCheck() {
    Map<String, Object> health = new HashMap<>();

    try {
      // 기본 정보
      health.put("service", "StockStatusService");
      health.put("timestamp", System.currentTimeMillis());

      // 웹소켓 상태
      boolean websocketConnected = webSocketClient.isConnected();
      health.put("websocketConnected", websocketConnected);

      // 시장 상태
      boolean marketOpen = isMarketOpen();
      health.put("marketOpen", marketOpen);

      // 데이터 통계
      var stats = getRealTimeDataStats();
      health.put("dataStats", stats);

      // Redis 연결 확인
      boolean redisConnected = false;
      try {
        redisTemplate.hasKey("test");
        redisConnected = true;
      } catch (Exception e) {
        log.error("Redis 연결 확인 실패", e);
      }
      health.put("redisConnected", redisConnected);

      // 전체 건강 상태 판단
      if (websocketConnected && redisConnected) {
        health.put("status", "HEALTHY");
        health.put("message", "시장 상태 서비스가 정상 작동 중입니다.");
      } else if (redisConnected) {
        health.put("status", "DEGRADED");
        health.put("message", "캐시는 정상이지만 실시간 데이터 수신에 문제가 있습니다.");
      } else {
        health.put("status", "UNHEALTHY");
        health.put("message", "시장 상태 서비스에 심각한 문제가 있습니다.");
      }

    } catch (Exception e) {
      log.error("시장 상태 헬스체크 실행 실패", e);
      health.put("status", "ERROR");
      health.put("message", "헬스체크 실행 중 오류 발생: " + e.getMessage());
    }

    return health;
  }

  // ========== 유틸리티 메서드 ==========

  /**
   * 실시간 데이터 존재 여부 확인
   */
  public boolean hasRealTimeData(String stockTicker) {
    if (stockTicker == null || stockTicker.trim().isEmpty()) {
      return false;
    }

    try {
      String cacheKey = "realtime:stock:" + stockTicker.trim();
      return redisTemplate.hasKey(cacheKey);
    } catch (Exception e) {
      log.error("실시간 데이터 존재 여부 확인 실패: {}", stockTicker, e);
      return false;
    }
  }

  /**
   * 전일 종가 데이터 존재 여부 확인
   */
  public boolean hasClosePriceData(String stockTicker) {
    if (stockTicker == null || stockTicker.trim().isEmpty()) {
      return false;
    }

    try {
      String cacheKey = "close:stock:" + stockTicker.trim();
      return redisTemplate.hasKey(cacheKey);
    } catch (Exception e) {
      log.error("전일 종가 데이터 존재 여부 확인 실패: {}", stockTicker, e);
      return false;
    }
  }

  /**
   * 시장 개장 여부를 특정 시간 기준으로 확인
   */
  public boolean isMarketOpenAt(LocalDateTime dateTime) {
    DayOfWeek dayOfWeek = dateTime.getDayOfWeek();
    LocalTime time = dateTime.toLocalTime();

    // 주말은 장외시간
    if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
      return false;
    }

    // 평일 09:00 ~ 15:30만 장내시간
    return !time.isBefore(MARKET_OPEN) && !time.isAfter(MARKET_CLOSE);
  }
}