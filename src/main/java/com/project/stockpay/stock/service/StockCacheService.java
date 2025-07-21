package com.project.stockpay.stock.service;

import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 주식 캐시 관리 서비스
 * - 실시간 데이터 캐시 갱신
 * - 주요 종목 구독 갱신
 * - 캐시 정리 및 최적화
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockCacheService {

  private final StockStatusService stockStatusService;
  private final RedisTemplate<String, Object> redisTemplate;

  // ========== 캐시 갱신 ==========

  /**
   * 캐시 갱신 (수동)
   */
  public void refreshCache(String stockTicker) {
    if (stockTicker == null || stockTicker.trim().isEmpty()) {
      log.warn("캐시 갱신 실패 - 종목 코드 없음");
      return;
    }

    try {
      String trimmedTicker = stockTicker.trim();

      // 기존 캐시 삭제
      redisTemplate.delete("realtime:stock:" + trimmedTicker);
      redisTemplate.delete("realtime:orderbook:" + trimmedTicker);

      // 새로운 구독 요청
      boolean subscribed = stockStatusService.subscribeStock(trimmedTicker);

      if (subscribed) {
        log.info("캐시 갱신 완료: {}", trimmedTicker);
      } else {
        log.warn("캐시 갱신 실패 - 구독 실패: {}", trimmedTicker);
      }
    } catch (Exception e) {
      log.error("캐시 갱신 실패: {}", stockTicker, e);
    }
  }

  /**
   * 다중 종목 캐시 갱신
   */
  public void refreshMultipleCache(List<String> stockTickers) {
    if (stockTickers == null || stockTickers.isEmpty()) {
      log.warn("다중 종목 캐시 갱신 실패 - 종목 코드 목록 없음");
      return;
    }

    log.info("다중 종목 캐시 갱신 시작: {}개 종목", stockTickers.size());

    int successCount = 0;
    int failCount = 0;

    for (String stockTicker : stockTickers) {
      try {
        refreshCache(stockTicker);
        successCount++;
        Thread.sleep(100); // 0.1초 간격 (API 부하 방지)
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.warn("다중 종목 캐시 갱신 중단됨");
        break;
      } catch (Exception e) {
        failCount++;
        log.error("종목 캐시 갱신 실패: {}", stockTicker, e);
      }
    }

    log.info("다중 종목 캐시 갱신 완료: 성공 {}개, 실패 {}개", successCount, failCount);
  }

  /**
   * 주요 종목 캐시 갱신
   */
  public void refreshPopularStocksCache() {
    log.info("주요 종목 캐시 갱신 시작");

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

    refreshMultipleCache(popularStocks);
    log.info("주요 종목 캐시 갱신 완료");
  }

  /**
   * 섹터별 종목 캐시 갱신
   */
  public void refreshSectorCache(String sector) {
    if (sector == null || sector.trim().isEmpty()) {
      log.warn("섹터별 캐시 갱신 실패 - 섹터명 없음");
      return;
    }

    log.info("섹터별 캐시 갱신 시작: {}", sector);

    try {
      // 섹터별 주요 종목 정의
      List<String> sectorStocks = getSectorStocks(sector.trim());

      if (sectorStocks.isEmpty()) {
        log.warn("섹터별 캐시 갱신 실패 - 해당 섹터 종목 없음: {}", sector);
        return;
      }

      refreshMultipleCache(sectorStocks);
      log.info("섹터별 캐시 갱신 완료: {} ({}개 종목)", sector, sectorStocks.size());

    } catch (Exception e) {
      log.error("섹터별 캐시 갱신 실패: {}", sector, e);
    }
  }

  /**
   * 섹터별 주요 종목 반환
   */
  private List<String> getSectorStocks(String sector) {
    return switch (sector.toLowerCase()) {
      case "반도체", "semiconductor" -> Arrays.asList(
          "005930", // 삼성전자
          "000660", // SK하이닉스
          "009150"  // 삼성전기
      );
      case "자동차", "automobile" -> Arrays.asList(
          "005380", // 현대차
          "000270", // 기아
          "012330"  // 현대모비스
      );
      case "화학", "chemical" -> Arrays.asList(
          "051910", // LG화학
          "006400", // 삼성SDI
          "096770"  // SK이노베이션
      );
      case "플랫폼", "platform", "it" -> Arrays.asList(
          "035420", // NAVER
          "035720"  // 카카오
      );
      case "바이오", "bio" -> Arrays.asList(
          "207940"  // 삼성바이오로직스
      );
      case "금융", "finance" -> Arrays.asList(
          "055550", // 신한지주
          "105560"  // KB금융
      );
      case "통신", "telecom" -> Arrays.asList(
          "017670", // SK텔레콤
          "030200"  // KT
      );
      default -> Arrays.asList(); // 빈 리스트
    };
  }

  // ========== 캐시 정리 ==========

  /**
   * 오래된 캐시 정리
   */
  public void cleanupStaleCache() {
    log.info("오래된 캐시 정리 시작");

    try {
      // 실시간 주가 캐시 정리
      int cleanedStockCount = cleanupStockCache();

      // 실시간 호가 캐시 정리
      int cleanedOrderbookCount = cleanupOrderbookCache();

      // 전일 종가 캐시 정리 (7일 이상 된 것)
      int cleanedClosePriceCount = cleanupClosePriceCache();

      log.info("오래된 캐시 정리 완료: 주가 {}개, 호가 {}개, 전일종가 {}개",
          cleanedStockCount, cleanedOrderbookCount, cleanedClosePriceCount);

    } catch (Exception e) {
      log.error("오래된 캐시 정리 실패", e);
    }
  }

  /**
   * 실시간 주가 캐시 정리
   */
  private int cleanupStockCache() {
    try {
      var stockKeys = redisTemplate.keys("realtime:stock:*");
      if (stockKeys == null || stockKeys.isEmpty()) {
        return 0;
      }

      int cleanedCount = 0;
      long currentTime = System.currentTimeMillis();

      for (String key : stockKeys) {
        try {
          var priceData = redisTemplate.opsForValue().get(key);
          if (priceData == null) {
            redisTemplate.delete(key);
            cleanedCount++;
          }
          // 추가적인 정리 로직이 필요하면 여기에 구현
        } catch (Exception e) {
          log.error("주가 캐시 정리 실패: {}", key, e);
        }
      }

      return cleanedCount;
    } catch (Exception e) {
      log.error("실시간 주가 캐시 정리 실패", e);
      return 0;
    }
  }

  /**
   * 실시간 호가 캐시 정리
   */
  private int cleanupOrderbookCache() {
    try {
      var orderbookKeys = redisTemplate.keys("realtime:orderbook:*");
      if (orderbookKeys == null || orderbookKeys.isEmpty()) {
        return 0;
      }

      int cleanedCount = 0;

      for (String key : orderbookKeys) {
        try {
          var orderbookData = redisTemplate.opsForValue().get(key);
          if (orderbookData == null) {
            redisTemplate.delete(key);
            cleanedCount++;
          }
          // 추가적인 정리 로직이 필요하면 여기에 구현
        } catch (Exception e) {
          log.error("호가 캐시 정리 실패: {}", key, e);
        }
      }

      return cleanedCount;
    } catch (Exception e) {
      log.error("실시간 호가 캐시 정리 실패", e);
      return 0;
    }
  }

  /**
   * 전일 종가 캐시 정리
   */
  private int cleanupClosePriceCache() {
    try {
      var closePriceKeys = redisTemplate.keys("close:stock:*");
      if (closePriceKeys == null || closePriceKeys.isEmpty()) {
        return 0;
      }

      int cleanedCount = 0;

      for (String key : closePriceKeys) {
        try {
          var closePrice = redisTemplate.opsForValue().get(key);
          if (closePrice == null) {
            redisTemplate.delete(key);
            cleanedCount++;
          }
          // 7일 이상 된 데이터는 자동으로 만료되므로 별도 처리 불필요
        } catch (Exception e) {
          log.error("전일 종가 캐시 정리 실패: {}", key, e);
        }
      }

      return cleanedCount;
    } catch (Exception e) {
      log.error("전일 종가 캐시 정리 실패", e);
      return 0;
    }
  }

  // ========== 캐시 통계 ==========

  /**
   * 캐시 통계 조회
   */
  public Map<String, Object> getCacheStats() {
    Map<String, Object> stats = new HashMap<>();

    try {
      // 실시간 주가 캐시 통계
      var stockKeys = redisTemplate.keys("realtime:stock:*");
      int stockCacheCount = stockKeys != null ? stockKeys.size() : 0;

      // 실시간 호가 캐시 통계
      var orderbookKeys = redisTemplate.keys("realtime:orderbook:*");
      int orderbookCacheCount = orderbookKeys != null ? orderbookKeys.size() : 0;

      // 전일 종가 캐시 통계
      var closePriceKeys = redisTemplate.keys("close:stock:*");
      int closePriceCacheCount = closePriceKeys != null ? closePriceKeys.size() : 0;

      stats.put("stockCache", stockCacheCount);
      stats.put("orderbookCache", orderbookCacheCount);
      stats.put("closePriceCache", closePriceCacheCount);
      stats.put("totalCache", stockCacheCount + orderbookCacheCount + closePriceCacheCount);
      stats.put("timestamp", System.currentTimeMillis());

      // 캐시 상태 평가
      if (stockCacheCount >= 10) {
        stats.put("status", "GOOD");
        stats.put("message", "캐시 상태 양호");
      } else if (stockCacheCount >= 5) {
        stats.put("status", "FAIR");
        stats.put("message", "캐시 데이터 부족");
      } else {
        stats.put("status", "POOR");
        stats.put("message", "캐시 데이터 매우 부족");
      }

    } catch (Exception e) {
      log.error("캐시 통계 조회 실패", e);
      stats.put("status", "ERROR");
      stats.put("message", "캐시 통계 조회 실패: " + e.getMessage());
    }

    return stats;
  }

  // ========== 프리워밍 ==========

  /**
   * 캐시 프리워밍 (애플리케이션 시작 시)
   */
  public void warmupCache() {
    log.info("캐시 프리워밍 시작");

    try {
      // 주요 종목 프리워밍
      refreshPopularStocksCache();

      // 잠시 대기 (구독 요청이 처리될 시간)
      Thread.sleep(3000);

      // 전일 종가 테스트 데이터 설정 (실제 데이터가 없는 경우)
      stockStatusService.setClosePricesForTesting();

      log.info("캐시 프리워밍 완료");

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("캐시 프리워밍 중단됨");
    } catch (Exception e) {
      log.error("캐시 프리워밍 실패", e);
    }
  }

  /**
   * 특정 섹터 프리워밍
   */
  public void warmupSectorCache(String sector) {
    log.info("섹터 캐시 프리워밍 시작: {}", sector);

    try {
      refreshSectorCache(sector);
      Thread.sleep(1000); // 1초 대기
      log.info("섹터 캐시 프리워밍 완료: {}", sector);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("섹터 캐시 프리워밍 중단됨: {}", sector);
    } catch (Exception e) {
      log.error("섹터 캐시 프리워밍 실패: {}", sector, e);
    }
  }

  // ========== 유틸리티 ==========

  /**
   * 캐시 키 존재 여부 확인
   */
  public boolean hasCacheKey(String key) {
    try {
      return redisTemplate.hasKey(key);
    } catch (Exception e) {
      log.error("캐시 키 존재 여부 확인 실패: {}", key, e);
      return false;
    }
  }

  /**
   * 특정 종목의 모든 캐시 삭제
   */
  public void clearStockCache(String stockTicker) {
    if (stockTicker == null || stockTicker.trim().isEmpty()) {
      return;
    }

    try {
      String trimmedTicker = stockTicker.trim();

      redisTemplate.delete("realtime:stock:" + trimmedTicker);
      redisTemplate.delete("realtime:orderbook:" + trimmedTicker);
      redisTemplate.delete("close:stock:" + trimmedTicker);

      log.info("종목 캐시 삭제 완료: {}", trimmedTicker);
    } catch (Exception e) {
      log.error("종목 캐시 삭제 실패: {}", stockTicker, e);
    }
  }

  /**
   * 모든 실시간 캐시 삭제 (위험한 작업)
   */
  public void clearAllRealtimeCache() {
    log.warn("모든 실시간 캐시 삭제 시작 - 주의: 위험한 작업입니다");

    try {
      var stockKeys = redisTemplate.keys("realtime:stock:*");
      var orderbookKeys = redisTemplate.keys("realtime:orderbook:*");

      int deletedCount = 0;

      if (stockKeys != null && !stockKeys.isEmpty()) {
        redisTemplate.delete(stockKeys);
        deletedCount += stockKeys.size();
      }


      log.warn("모든 실시간 캐시 삭제 완료: {}개 키 삭제됨", deletedCount);

    } catch (Exception e) {
      log.error("모든 실시간 캐시 삭제 실패", e);
    }
  }
}