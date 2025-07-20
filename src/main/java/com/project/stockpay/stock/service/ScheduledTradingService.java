package com.project.stockpay.stock.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;


import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 스케줄링 서비스
 * - 예약 주문 처리 (개장 시)
 * - 미체결 주문 정리 (장 마감 시)
 * - 주가 데이터 정리 작업
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledTradingService {

  private final TradingService tradingService;
  private final StockPriceService stockPriceService;
  private final StockStatusService stockStatusService;

  /**
   * 개장 시 예약 주문 처리
   * 매일 평일 09:00에 실행
   */
  @Scheduled(cron = "0 0 9 * * MON-FRI", zone = "Asia/Seoul")
  public void processReservedOrdersOnMarketOpen() {
    log.info("=== 개장 시 예약 주문 처리 시작 ===");

    try {
      // 시장 개장 확인
      if (!stockStatusService.isMarketOpen()) {
        log.warn("시장이 개장되지 않았습니다. 예약 주문 처리를 건너뜁니다.");
        return;
      }

      // 예약 주문을 일반 주문으로 전환
      tradingService.processReservedOrders();

      log.info("=== 개장 시 예약 주문 처리 완료 ===");

    } catch (Exception e) {
      log.error("개장 시 예약 주문 처리 실패", e);
    }
  }

  /**
   * 장 마감 시 미체결 주문 정리
   * 매일 평일 15:35에 실행 (장 마감 5분 후)
   */
  @Scheduled(cron = "0 35 15 * * MON-FRI", zone = "Asia/Seoul")
  public void cleanupPendingOrdersOnMarketClose() {
    log.info("=== 장 마감 시 미체결 주문 정리 시작 ===");

    try {
      // TODO: 미체결 주문 정리 로직 구현
      // 1. 모든 미체결 주문 조회
      // 2. 주문 취소 처리
      // 3. 잔고 원복 (매수 주문의 경우)

      log.info("=== 장 마감 시 미체결 주문 정리 완료 ===");

    } catch (Exception e) {
      log.error("장 마감 시 미체결 주문 정리 실패", e);
    }
  }

  /**
   * 주가 데이터 정리 작업
   * 매일 자정에 실행
   */
  @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
  public void dailyDataCleanup() {
    log.info("=== 일일 데이터 정리 작업 시작 ===");

    try {
      // TODO: 일일 데이터 정리 작업
      // 1. 오래된 Redis 캐시 정리
      // 2. 주가 이력 데이터 정리
      // 3. 로그 정리

      log.info("=== 일일 데이터 정리 작업 완료 ===");

    } catch (Exception e) {
      log.error("일일 데이터 정리 작업 실패", e);
    }
  }

  /**
   * 시스템 상태 체크
   * 매 30분마다 실행
   */
  @Scheduled(fixedRate = 1800000) // 30분 = 1800000ms
  public void systemHealthCheck() {
    try {
      String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

      // 시장 상태 체크
      boolean isMarketOpen = stockStatusService.isMarketOpen();

      // 실시간 데이터 통계 체크
      var stats = stockStatusService.getRealTimeDataStats();

      log.info("=== 시스템 상태 체크 ({}) ===", currentTime);
      log.info("시장 상태: {}", isMarketOpen ? "개장" : "휴장");
      log.info("실시간 데이터 통계: {}", stats);

    } catch (Exception e) {
      log.error("시스템 상태 체크 실패", e);
    }
  }

  /**
   * 주요 종목 실시간 데이터 구독 갱신
   * 매일 평일 08:50에 실행 (개장 10분 전)
   */
  @Scheduled(cron = "0 50 8 * * MON-FRI", zone = "Asia/Seoul")
  public void refreshPopularStockSubscriptions() {
    log.info("=== 주요 종목 실시간 데이터 구독 갱신 시작 ===");

    try {
      // 주요 종목들 구독 갱신
      String[] popularStocks = {
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
      };

      for (String stockTicker : popularStocks) {
        try {
          stockStatusService.subscribeStock(stockTicker);
          Thread.sleep(100); // 0.1초 간격
        } catch (Exception e) {
          log.error("종목 구독 갱신 실패: {}", stockTicker, e);
        }
      }

      log.info("=== 주요 종목 실시간 데이터 구독 갱신 완료 ===");

    } catch (Exception e) {
      log.error("주요 종목 실시간 데이터 구독 갱신 실패", e);
    }
  }
}