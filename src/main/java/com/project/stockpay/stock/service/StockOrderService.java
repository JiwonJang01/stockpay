package com.project.stockpay.stock.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.stockpay.common.entity.StockBuy;
import com.project.stockpay.common.entity.StockSell;
import com.project.stockpay.common.repository.StockBuyRepository;
import com.project.stockpay.common.repository.StockSellRepository;
import com.project.stockpay.stock.dto.OrderMessage;
import com.project.stockpay.stock.dto.OrderRetryStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka 기반 비동기 주식 주문 처리 서비스
 * - Kafka 메시지 소비 및 체결 처리
 * - 확률 기반 체결 처리 (65~75%)
 * - 3분 간격 재시도 메커니즘 (최대 5회)
 * - 5회 실패 시 100% 강제 체결
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockOrderService {

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final RedisTemplate<String, Object> redisTemplate;
  private final TradingService tradingService;
  private final StockBuyRepository stockBuyRepository;
  private final StockSellRepository stockSellRepository;
  private final ObjectMapper objectMapper;

  // === Kafka, Redis 설정 (properties에서 주입) ===

  // Kafka 토픽명
  @Value("${stockpay.kafka.topics.buy-orders:buy-orders}")
  private String BUY_ORDERS_TOPIC;

  @Value("${stockpay.kafka.topics.sell-orders:sell-orders}")
  private String SELL_ORDERS_TOPIC;

  @Value("${stockpay.kafka.topics.buy-retry:buy-orders-retry}")
  private String BUY_RETRY_TOPIC;

  @Value("${stockpay.kafka.topics.sell-retry:sell-orders-retry}")
  private String SELL_RETRY_TOPIC;

  // Redis 키 패턴
  @Value("${stockpay.redis.keys.retry-count:retry:count:}")
  private String RETRY_COUNT_KEY;

  @Value("${stockpay.redis.keys.retry-delay:retry:delay:}")
  private String RETRY_DELAY_KEY;

  // 재시도 설정
  @Value("${stockpay.trading.retry.max-count:5}")
  private int MAX_RETRY_COUNT;

  @Value("${stockpay.trading.retry.delay-minutes:3}")
  private long RETRY_DELAY_MINUTES;

  // ========== Kafka 리스너 (Consumer) ==========

  /**
   * 매수 주문 처리 리스너
   */
  @KafkaListener(topics = "${stockpay.kafka.topics.buy-orders}", groupId = "${spring.kafka.consumer.group-id}")
  @Transactional
  public void processBuyOrder(
      @Payload String message,
      @Header(KafkaHeaders.RECEIVED_KEY) String key,
      Acknowledgment ack) {

    log.info("매수 주문 처리 시작: key={}, message={}", key, message);

    try {
      OrderMessage orderMessage = objectMapper.readValue(message, OrderMessage.class);
      String stockbuyId = orderMessage.getOrderId();

      // 주문 상태 확인
      StockBuy stockBuy = stockBuyRepository.findById(stockbuyId).orElse(null);
      if (stockBuy == null) {
        log.warn("매수 주문을 찾을 수 없음: stockbuyId={}", stockbuyId);
        ack.acknowledge();
        return;
      }

      if (!"PENDING".equals(stockBuy.getStockbuyStatus())) {
        log.info("이미 처리된 매수 주문: stockbuyId={}, status={}", stockbuyId,
            stockBuy.getStockbuyStatus());
        ack.acknowledge();
        return;
      }

      // 체결 처리 시도
      boolean isExecuted = processBuyOrderExecution(stockbuyId, orderMessage.getRetryCount());

      if (isExecuted) {
        log.info("매수 주문 체결 성공: stockbuyId={}", stockbuyId);
        // Redis에서 재시도 관련 데이터 삭제
        cleanupRetryData(stockbuyId);
      } else {
        log.info("매수 주문 체결 실패 - 재시도 스케줄링: stockbuyId={}", stockbuyId);
        scheduleRetry(stockbuyId, "BUY", orderMessage.getRetryCount());
      }

      ack.acknowledge();

    } catch (Exception e) {
      log.error("매수 주문 처리 중 오류: key={}", key, e);
      ack.acknowledge(); // 에러 발생 시에도 ack하여 무한 재처리 방지
    }
  }

  /**
   * 매도 주문 처리 리스너
   */
  @KafkaListener(topics = "${stockpay.kafka.topics.sell-orders}", groupId = "${spring.kafka.consumer.group-id}")
  @Transactional
  public void processSellOrder(
      @Payload String message,
      @Header(KafkaHeaders.RECEIVED_KEY) String key,
      Acknowledgment ack) {

    log.info("매도 주문 처리 시작: key={}, message={}", key, message);

    try {
      OrderMessage orderMessage = objectMapper.readValue(message, OrderMessage.class);
      String stocksellId = orderMessage.getOrderId();

      // 주문 상태 확인
      StockSell stockSell = stockSellRepository.findById(stocksellId).orElse(null);
      if (stockSell == null) {
        log.warn("매도 주문을 찾을 수 없음: stocksellId={}", stocksellId);
        ack.acknowledge();
        return;
      }

      if (!"PENDING".equals(stockSell.getStocksellStatus())) {
        log.info("이미 처리된 매도 주문: stocksellId={}, status={}", stocksellId,
            stockSell.getStocksellStatus());
        ack.acknowledge();
        return;
      }

      // 체결 처리 시도
      boolean isExecuted = processSellOrderExecution(stocksellId, orderMessage.getRetryCount());

      if (isExecuted) {
        log.info("매도 주문 체결 성공: stocksellId={}", stocksellId);
        // Redis에서 재시도 관련 데이터 삭제
        cleanupRetryData(stocksellId);
      } else {
        log.info("매도 주문 체결 실패 - 재시도 스케줄링: stocksellId={}", stocksellId);
        scheduleRetry(stocksellId, "SELL", orderMessage.getRetryCount());
      }

      ack.acknowledge();

    } catch (Exception e) {
      log.error("매도 주문 처리 중 오류: key={}", key, e);
      ack.acknowledge(); // 에러 발생 시에도 ack하여 무한 재처리 방지
    }
  }

  /**
   * 매수 주문 재시도 리스너
   */
  @KafkaListener(topics = "${stockpay.kafka.topics.buy-retry}", groupId = "stockpay-retry-group")
  @Transactional
  public void processBuyOrderRetry(
      @Payload String message,
      @Header(KafkaHeaders.RECEIVED_KEY) String key,
      Acknowledgment ack) {

    log.info("매수 주문 재시도 처리: key={}, message={}", key, message);

    try {
      OrderMessage orderMessage = objectMapper.readValue(message, OrderMessage.class);
      String stockbuyId = orderMessage.getOrderId();

      // 재시도 지연 확인
      if (!isRetryTimeReached(stockbuyId)) {
        log.debug("재시도 시간이 아직 되지 않음: stockbuyId={}", stockbuyId);
        ack.acknowledge();
        return;
      }

      // 원래 토픽으로 다시 발행
      publishBuyOrderRetry(stockbuyId, orderMessage.getRetryCount());
      ack.acknowledge();

    } catch (Exception e) {
      log.error("매수 주문 재시도 처리 중 오류: key={}", key, e);
      ack.acknowledge();
    }
  }

  /**
   * 매도 주문 재시도 리스너
   */
  @KafkaListener(topics = "${stockpay.kafka.topics.sell-retry}", groupId = "stockpay-retry-group")
  @Transactional
  public void processSellOrderRetry(
      @Payload String message,
      @Header(KafkaHeaders.RECEIVED_KEY) String key,
      Acknowledgment ack) {

    log.info("매도 주문 재시도 처리: key={}, message={}", key, message);

    try {
      OrderMessage orderMessage = objectMapper.readValue(message, OrderMessage.class);
      String stocksellId = orderMessage.getOrderId();

      // 재시도 지연 확인
      if (!isRetryTimeReached(stocksellId)) {
        log.debug("재시도 시간이 아직 되지 않음: stocksellId={}", stocksellId);
        ack.acknowledge();
        return;
      }

      // 원래 토픽으로 다시 발행
      publishSellOrderRetry(stocksellId, orderMessage.getRetryCount());
      ack.acknowledge();

    } catch (Exception e) {
      log.error("매도 주문 재시도 처리 중 오류: key={}", key, e);
      ack.acknowledge();
    }
  }

  // ========== 체결 처리 메서드 ==========

  /**
   * 매수 주문 체결 실행 (확률 기반)
   */
  private boolean processBuyOrderExecution(String stockbuyId, int retryCount) {
    log.info("매수 주문 체결 시도: stockbuyId={}, retryCount={}", stockbuyId, retryCount);

    try {
      // 5회 재시도 후에는 100% 체결
      if (retryCount >= MAX_RETRY_COUNT) {
        log.info("최대 재시도 횟수 도달 - 강제 체결: stockbuyId={}", stockbuyId);
        tradingService.processBuyOrderForced(stockbuyId);
        return true;
      }

      // TradingService의 확률 기반 체결 로직 사용
      tradingService.processBuyOrder(stockbuyId);

      // 체결 상태 확인
      StockBuy stockBuy = stockBuyRepository.findById(stockbuyId).orElse(null);
      if (stockBuy != null && "EXECUTED".equals(stockBuy.getStockbuyStatus())) {
        return true;
      }

      return false; // 체결 실패

    } catch (Exception e) {
      log.error("매수 주문 체결 처리 중 오류: stockbuyId={}", stockbuyId, e);
      return false;
    }
  }

  /**
   * 매도 주문 체결 실행 (확률 기반)
   */
  private boolean processSellOrderExecution(String stocksellId, int retryCount) {
    log.info("매도 주문 체결 시도: stocksellId={}, retryCount={}", stocksellId, retryCount);

    try {
      // 5회 재시도 후에는 100% 체결
      if (retryCount >= MAX_RETRY_COUNT) {
        log.info("최대 재시도 횟수 도달 - 강제 체결: stocksellId={}", stocksellId);
        tradingService.processSellOrderForced(stocksellId);
        return true;
      }

      // TradingService의 확률 기반 체결 로직 사용
      tradingService.processSellOrder(stocksellId);

      // 체결 상태 확인
      StockSell stockSell = stockSellRepository.findById(stocksellId).orElse(null);
      if (stockSell != null && "EXECUTED".equals(stockSell.getStocksellStatus())) {
        return true;
      }

      return false; // 체결 실패

    } catch (Exception e) {
      log.error("매도 주문 체결 처리 중 오류: stocksellId={}", stocksellId, e);
      return false;
    }
  }

  // ========== 재시도 메커니즘 ==========

  /**
   * 재시도 스케줄링
   */
  private void scheduleRetry(String orderId, String orderType, int currentRetryCount) {
    int newRetryCount = currentRetryCount + 1;

    if (newRetryCount > MAX_RETRY_COUNT) {
      log.warn("최대 재시도 횟수 초과: orderId={}, retryCount={}", orderId, newRetryCount);
      return;
    }

    log.info("재시도 스케줄링: orderId={}, orderType={}, retryCount={}", orderId, orderType,
        newRetryCount);

    try {
      // Redis에 재시도 카운트와 다음 실행 시간 저장
      redisTemplate.opsForValue()
          .set(RETRY_COUNT_KEY + orderId, newRetryCount, Duration.ofHours(24));
      long nextRetryTime = System.currentTimeMillis() + (RETRY_DELAY_MINUTES * 60 * 1000);
      redisTemplate.opsForValue()
          .set(RETRY_DELAY_KEY + orderId, nextRetryTime, Duration.ofHours(24));

      // 재시도 토픽으로 메시지 발행 (지연 처리)
      OrderMessage retryMessage = OrderMessage.builder()
          .orderId(orderId)
          .orderType(orderType)
          .timestamp(System.currentTimeMillis())
          .retryCount(newRetryCount)
          .nextRetryTime(nextRetryTime)
          .build();

      String messageJson = objectMapper.writeValueAsString(retryMessage);
      String retryTopic = "BUY".equals(orderType) ? BUY_RETRY_TOPIC : SELL_RETRY_TOPIC;

      kafkaTemplate.send(retryTopic, orderId, messageJson);

      log.info("재시도 메시지 발행 완료: orderId={}, nextRetryTime={}", orderId,
          LocalDateTime.now().plusMinutes(RETRY_DELAY_MINUTES));

    } catch (Exception e) {
      log.error("재시도 스케줄링 실패: orderId={}", orderId, e);
    }
  }

  /**
   * 재시도 시간 도달 여부 확인
   */
  private boolean isRetryTimeReached(String orderId) {
    try {
      Long nextRetryTime = (Long) redisTemplate.opsForValue().get(RETRY_DELAY_KEY + orderId);
      if (nextRetryTime == null) {
        return true; // 재시도 시간 정보가 없으면 바로 실행
      }

      return System.currentTimeMillis() >= nextRetryTime;

    } catch (Exception e) {
      log.error("재시도 시간 확인 실패: orderId={}", orderId, e);
      return true; // 에러 시 바로 실행
    }
  }

  /**
   * 매수 주문 재시도 발행
   */
  private void publishBuyOrderRetry(String stockbuyId, int retryCount) {
    try {
      OrderMessage orderMessage = OrderMessage.builder()
          .orderId(stockbuyId)
          .orderType("BUY")
          .timestamp(System.currentTimeMillis())
          .retryCount(retryCount)
          .build();

      String messageJson = objectMapper.writeValueAsString(orderMessage);
      kafkaTemplate.send(BUY_ORDERS_TOPIC, stockbuyId, messageJson);

      log.info("매수 주문 재시도 발행: stockbuyId={}, retryCount={}", stockbuyId, retryCount);

    } catch (Exception e) {
      log.error("매수 주문 재시도 발행 실패: stockbuyId={}", stockbuyId, e);
    }
  }

  /**
   * 매도 주문 재시도 발행
   */
  private void publishSellOrderRetry(String stocksellId, int retryCount) {
    try {
      OrderMessage orderMessage = OrderMessage.builder()
          .orderId(stocksellId)
          .orderType("SELL")
          .timestamp(System.currentTimeMillis())
          .retryCount(retryCount)
          .build();

      String messageJson = objectMapper.writeValueAsString(orderMessage);
      kafkaTemplate.send(SELL_ORDERS_TOPIC, stocksellId, messageJson);

      log.info("매도 주문 재시도 발행: stocksellId={}, retryCount={}", stocksellId, retryCount);

    } catch (Exception e) {
      log.error("매도 주문 재시도 발행 실패: stocksellId={}", stocksellId, e);
    }
  }

  /**
   * 재시도 관련 데이터 정리
   */
  private void cleanupRetryData(String orderId) {
    try {
      redisTemplate.delete(RETRY_COUNT_KEY + orderId);
      redisTemplate.delete(RETRY_DELAY_KEY + orderId);
      log.debug("재시도 데이터 정리 완료: orderId={}", orderId);
    } catch (Exception e) {
      log.error("재시도 데이터 정리 실패: orderId={}", orderId, e);
    }
  }

  // ========== 상태 조회 메서드 ==========

  /**
   * 주문 재시도 상태 조회
   */
  public OrderRetryStatus getRetryStatus(String orderId) {
    try {
      Integer retryCount = (Integer) redisTemplate.opsForValue().get(RETRY_COUNT_KEY + orderId);
      Long nextRetryTime = (Long) redisTemplate.opsForValue().get(RETRY_DELAY_KEY + orderId);

      return OrderRetryStatus.builder()
          .orderId(orderId)
          .retryCount(retryCount != null ? retryCount : 0)
          .maxRetryCount(MAX_RETRY_COUNT)
          .nextRetryTime(nextRetryTime)
          .isMaxRetryReached(retryCount != null && retryCount >= MAX_RETRY_COUNT)
          .build();

    } catch (Exception e) {
      log.error("재시도 상태 조회 실패: orderId={}", orderId, e);
      return OrderRetryStatus.builder()
          .orderId(orderId)
          .retryCount(0)
          .maxRetryCount(MAX_RETRY_COUNT)
          .isMaxRetryReached(false)
          .build();
    }
  }
}