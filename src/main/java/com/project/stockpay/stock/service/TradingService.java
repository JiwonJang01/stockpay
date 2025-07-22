package com.project.stockpay.stock.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.stockpay.common.account.service.AccountService;
import com.project.stockpay.common.entity.*;
import com.project.stockpay.common.repository.*;
import com.project.stockpay.stock.dto.BuyOrderRequestDto;
import com.project.stockpay.stock.dto.OrderMessage;
import com.project.stockpay.stock.dto.SellOrderRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * 주식 거래 서비스
 * - 주문 접수 및 검증
 * - Kafka 메시지 발행
 * - 체결 처리 (StockOrderService에서 호출)
 * - 예약 주문 처리
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TradingService {

  // === 핵심 서비스 의존성 ===
  private final StockPriceService stockPriceService;
  private final StockStatusService stockStatusService;
  private final StockUtilService stockUtilService;
  private final AccountService accountService;

  // === 리포지토리 의존성 ===
  private final StockBuyRepository stockBuyRepository;
  private final StockSellRepository stockSellRepository;
  private final HoldingRepository holdingRepository;
  private final AccountRepository accountRepository;
  private final StockRepository stockRepository;
  private final AccountHistoryRepository accountHistoryRepository;

  // === Kafka 의존성 (순환 참조 해결) ===
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;

  private final Random random = new Random();

  // === Kafka 설정 (properties에서 주입) ===
  @Value("${stockpay.kafka.topics.buy-orders:buy-orders}")
  private String BUY_ORDERS_TOPIC;

  @Value("${stockpay.kafka.topics.sell-orders:sell-orders}")
  private String SELL_ORDERS_TOPIC;

  // TODO: Builder 적용필요

  // ========== 주문 접수 메서드 ==========

  /**
   * 매수 주문 접수 (DTO 기반)
   */
  public String submitBuyOrder(BuyOrderRequestDto request) {
    return submitBuyOrder(request.getUserId(), request.getStockTicker(),
        request.getQuantity(), request.getPrice());
  }

  /**
   * 매도 주문 접수 (DTO 기반)
   */
  public String submitSellOrder(SellOrderRequestDto request) {
    return submitSellOrder(request.getUserId(), request.getStockTicker(),
        request.getQuantity(), request.getPrice());
  }

  /**
   * 현재가 기준 매수 주문 접수
   */
  public String submitBuyOrderAtMarketPrice(String userId, String stockTicker, Integer quantity) {
    log.info("시장가 매수 주문 접수: userId={}, stockTicker={}, quantity={}", userId, stockTicker, quantity);

    Integer currentPrice = stockPriceService.getCurrentPrice(stockTicker);
    if (currentPrice == null) {
      throw new RuntimeException("현재가 조회 실패: " + stockTicker);
    }

    return submitBuyOrder(userId, stockTicker, quantity, currentPrice);
  }

  /**
   * 현재가 기준 매도 주문 접수
   */
  public String submitSellOrderAtMarketPrice(String userId, String stockTicker, Integer quantity) {
    log.info("시장가 매도 주문 접수: userId={}, stockTicker={}, quantity={}", userId, stockTicker, quantity);

    Integer currentPrice = stockPriceService.getCurrentPrice(stockTicker);
    if (currentPrice == null) {
      throw new RuntimeException("현재가 조회 실패: " + stockTicker);
    }

    return submitSellOrder(userId, stockTicker, quantity, currentPrice);
  }

  // ========== 핵심 주문 처리 메서드 ==========

  /**
   * 매수 주문 접수
   */
  public String submitBuyOrder(String userId, String stockTicker, Integer quantity, Integer price) {
    log.info("매수 주문 접수 시작: userId={}, stockTicker={}, quantity={}, price={}",
        userId, stockTicker, quantity, price);

    try {
      // 1. 입력 검증
      log.debug("1. 입력 검증 시작");
      validateBuyOrderInput(userId, stockTicker, quantity, price);

      // 2. 종목 코드 정규화 및 검증
      log.debug("2. 종목 코드 정규화");
      String normalizedTicker = stockUtilService.normalizeStockTicker(stockTicker);
      if (!stockUtilService.isValidStockTicker(normalizedTicker)) {
        throw new RuntimeException("유효하지 않은 종목 코드: " + stockTicker);
      }

      // 3. 가격이 null이면 현재가로 설정
      log.debug("3. 종목 코드 검증");
      if (price == null) {
        price = stockPriceService.getCurrentPrice(normalizedTicker);
        if (price == null) {
          throw new RuntimeException("가격 정보를 가져올 수 없습니다: " + normalizedTicker);
        }
      }

      // 4. 총 필요 금액 계산
      Integer totalAmount = price * quantity;

      // 5. 계좌 조회 및 검증
      Account account = getAccountByUserId(userId);

      // 6. 잔고 확인
      if (!accountService.canBuy(userId, totalAmount)) {
        Integer currentBalance = accountService.getBalance(userId);
        throw new RuntimeException(String.format(
            "잔고 부족: 필요금액=%,d원, 현재잔고=%,d원, 부족금액=%,d원",
            totalAmount, currentBalance, totalAmount - currentBalance));
      }

      // 7. 종목 정보 확인
      Stock stock = getStockByTicker(normalizedTicker);
      if (stock == null) {
        throw new RuntimeException("종목을 찾을 수 없습니다: stockTicker=" + normalizedTicker);
      }

      // 8. 매수 주문 생성
      String stockbuyId = generateId(IdType.STOCK_BUY);
      StockBuy stockBuy = createStockBuyEntity(stockbuyId, price, quantity, account, stock);

      stockBuyRepository.save(stockBuy);

      // 9. 잔고 차감 (예약 주문도 미리 차감)
      accountService.deductBalance(userId, totalAmount);

      // 10. 장내시간이면 Kafka로 비동기 체결 처리 요청
      if (stockStatusService.isMarketOpen() && "PENDING".equals(stockBuy.getStockbuyStatus())) {
        publishBuyOrderToKafka(stockbuyId);
        log.info("매수 주문 Kafka 발행 완료: stockbuyId={}", stockbuyId);
      }

      log.info("매수 주문 접수 완료: stockbuyId={}, status={}, totalAmount={}",
          stockbuyId, stockBuy.getStockbuyStatus(), totalAmount);

      return stockbuyId;

    } catch (Exception e) {
      log.error("매수 주문 접수 실패: userId={}, stockTicker={}, error={}",
          userId, stockTicker, e.getMessage());
      throw e;
    }
  }

  /**
   * 매도 주문 접수
   */
  public String submitSellOrder(String userId, String stockTicker, Integer quantity, Integer price) {
    log.info("매도 주문 접수 시작: userId={}, stockTicker={}, quantity={}, price={}",
        userId, stockTicker, quantity, price);

    try {
      // 1. 입력 검증
      validateSellOrderInput(userId, stockTicker, quantity, price);

      // 2. 종목 코드 정규화 및 검증
      String normalizedTicker = stockUtilService.normalizeStockTicker(stockTicker);
      if (!stockUtilService.isValidStockTicker(normalizedTicker)) {
        throw new RuntimeException("유효하지 않은 종목 코드: " + stockTicker);
      }

      // 3. 가격이 null이면 현재가로 설정
      if (price == null) {
        price = stockPriceService.getCurrentPrice(normalizedTicker);
        if (price == null) {
          throw new RuntimeException("가격 정보를 가져올 수 없습니다: " + normalizedTicker);
        }
      }

      // 4. 계좌 조회
      Account account = getAccountByUserId(userId);

      // 5. 보유 주식 조회 및 검증
      Holding holding = getHoldingByAccountAndStock(account, normalizedTicker);
      if (holding == null) {
        throw new RuntimeException("보유 주식이 없습니다: stockTicker=" + normalizedTicker);
      }

      // 6. 보유 수량 확인
      if (holding.getHoldNum() < quantity) {
        throw new RuntimeException(String.format(
            "보유 수량 부족: 보유수량=%,d주, 매도요청=%,d주, 부족수량=%,d주",
            holding.getHoldNum(), quantity, quantity - holding.getHoldNum()));
      }

      // 7. 종목 정보 확인
      Stock stock = holding.getStock();
      if (stock == null) {
        stock = getStockByTicker(normalizedTicker);
        if (stock == null) {
          throw new RuntimeException("종목을 찾을 수 없습니다: stockTicker=" + normalizedTicker);
        }
      }

      // 8. 매도 주문 생성
      String stocksellId = generateId(IdType.STOCK_SELL);
      StockSell stockSell = createStockSellEntity(stocksellId, price, quantity, account, holding);

      stockSellRepository.save(stockSell);

      // 9. 장내시간이면 Kafka로 비동기 체결 처리 요청
      if (stockStatusService.isMarketOpen() && "PENDING".equals(stockSell.getStocksellStatus())) {
        publishSellOrderToKafka(stocksellId);
        log.info("매도 주문 Kafka 발행 완료: stocksellId={}", stocksellId);
      }

      log.info("매도 주문 접수 완료: stocksellId={}, status={}",
          stocksellId, stockSell.getStocksellStatus());

      return stocksellId;

    } catch (Exception e) {
      log.error("매도 주문 접수 실패: userId={}, stockTicker={}, error={}",
          userId, stockTicker, e.getMessage());
      throw e;
    }
  }

  // ========== Kafka 메시지 발행 ==========

  /**
   * 매수 주문을 Kafka로 발행
   */
  private void publishBuyOrderToKafka(String stockbuyId) {
    try {
      OrderMessage orderMessage = OrderMessage.builder()
          .orderId(stockbuyId)
          .orderType("BUY")
          .timestamp(System.currentTimeMillis())
          .retryCount(0)
          .build();

      String messageJson = objectMapper.writeValueAsString(orderMessage);
      kafkaTemplate.send(BUY_ORDERS_TOPIC, stockbuyId, messageJson);

      log.debug("매수 주문 Kafka 발행: stockbuyId={}", stockbuyId);
    } catch (Exception e) {
      log.error("매수 주문 Kafka 발행 실패: stockbuyId={}", stockbuyId, e);
    }
  }

  /**
   * 매도 주문을 Kafka로 발행
   */
  private void publishSellOrderToKafka(String stocksellId) {
    try {
      OrderMessage orderMessage = OrderMessage.builder()
          .orderId(stocksellId)
          .orderType("SELL")
          .timestamp(System.currentTimeMillis())
          .retryCount(0)
          .build();

      String messageJson = objectMapper.writeValueAsString(orderMessage);
      kafkaTemplate.send(SELL_ORDERS_TOPIC, stocksellId, messageJson);

      log.debug("매도 주문 Kafka 발행: stocksellId={}", stocksellId);
    } catch (Exception e) {
      log.error("매도 주문 Kafka 발행 실패: stocksellId={}", stocksellId, e);
    }
  }

  // ========== 주문 체결 처리 (StockOrderService에서 호출) ==========

  /**
   * 매수 주문 체결 처리 (확률 기반)
   */
  public void processBuyOrder(String stockbuyId) {
    log.info("매수 주문 체결 처리 시작: stockbuyId={}", stockbuyId);

    StockBuy stockBuy = stockBuyRepository.findById(stockbuyId)
        .orElseThrow(() -> new RuntimeException("매수 주문을 찾을 수 없습니다: " + stockbuyId));

    if (!"PENDING".equals(stockBuy.getStockbuyStatus())) {
      log.warn("이미 처리된 주문: stockbuyId={}, status={}", stockbuyId, stockBuy.getStockbuyStatus());
      return;
    }

    boolean isExecuted = shouldExecuteOrder();

    if (isExecuted) {
      executeBuyOrder(stockBuy);
    } else {
      log.info("매수 주문 체결 실패 - 재시도 대기: stockbuyId={}", stockbuyId);
    }
  }

  /**
   * 매도 주문 체결 처리 (확률 기반)
   */
  public void processSellOrder(String stocksellId) {
    log.info("매도 주문 체결 처리 시작: stocksellId={}", stocksellId);

    StockSell stockSell = stockSellRepository.findById(stocksellId)
        .orElseThrow(() -> new RuntimeException("매도 주문을 찾을 수 없습니다: " + stocksellId));

    if (!"PENDING".equals(stockSell.getStocksellStatus())) {
      log.warn("이미 처리된 주문: stocksellId={}, status={}", stocksellId, stockSell.getStocksellStatus());
      return;
    }

    boolean isExecuted = shouldExecuteOrder();

    if (isExecuted) {
      executeSellOrder(stockSell);
    } else {
      log.info("매도 주문 체결 실패 - 재시도 대기: stocksellId={}", stocksellId);
    }
  }

  /**
   * 매수 주문 강제 체결 (5회 재시도 후 100% 체결)
   */
  public void processBuyOrderForced(String stockbuyId) {
    log.info("매수 주문 강제 체결 시작: stockbuyId={}", stockbuyId);

    StockBuy stockBuy = stockBuyRepository.findById(stockbuyId)
        .orElseThrow(() -> new RuntimeException("매수 주문을 찾을 수 없습니다: " + stockbuyId));

    if (!"PENDING".equals(stockBuy.getStockbuyStatus())) {
      log.warn("이미 처리된 주문 - 강제 체결 불가: stockbuyId={}, status={}",
          stockbuyId, stockBuy.getStockbuyStatus());
      return;
    }

    try {
      executeBuyOrder(stockBuy);
      log.info("매수 주문 강제 체결 완료: stockbuyId={}", stockbuyId);
    } catch (Exception e) {
      log.error("매수 주문 강제 체결 실패: stockbuyId={}", stockbuyId, e);
      throw e;
    }
  }

  /**
   * 매도 주문 강제 체결 (5회 재시도 후 100% 체결)
   */
  public void processSellOrderForced(String stocksellId) {
    log.info("매도 주문 강제 체결 시작: stocksellId={}", stocksellId);

    StockSell stockSell = stockSellRepository.findById(stocksellId)
        .orElseThrow(() -> new RuntimeException("매도 주문을 찾을 수 없습니다: " + stocksellId));

    if (!"PENDING".equals(stockSell.getStocksellStatus())) {
      log.warn("이미 처리된 주문 - 강제 체결 불가: stocksellId={}, status={}",
          stocksellId, stockSell.getStocksellStatus());
      return;
    }

    try {
      executeSellOrder(stockSell);
      log.info("매도 주문 강제 체결 완료: stocksellId={}", stocksellId);
    } catch (Exception e) {
      log.error("매도 주문 강제 체결 실패: stocksellId={}", stocksellId, e);
      throw e;
    }
  }

  // ========== 예약 주문 처리 ==========

  /**
   * 예약 주문을 일반 주문으로 전환 (개장 시)
   */
  public void processReservedOrders() {
    log.info("예약 주문 처리 시작");

    try {
      // 예약된 매수 주문 처리
      List<StockBuy> reservedBuyOrders = stockBuyRepository.findByStockbuyStatus("RESERVED");
      for (StockBuy buyOrder : reservedBuyOrders) {
        buyOrder.setStockbuyStatus("PENDING");
        buyOrder.setStockbuyChangetime(Timestamp.valueOf(LocalDateTime.now()));

        // 개장 시 실제 시세로 가격 업데이트
        Integer currentPrice = stockPriceService.getCurrentPrice(buyOrder.getStock().getStockTicker());
        if (currentPrice != null) {
          Integer oldPrice = buyOrder.getStockbuyPrice();
          Integer priceDiff = (currentPrice - oldPrice) * buyOrder.getStockbuyNum();

          // 가격 차이만큼 잔고 조정
          Account account = buyOrder.getAccount();
          if (priceDiff > 0) {
            // 가격 상승: 추가 차감 필요
            if (accountService.canBuy(account.getUserId(), priceDiff)) {
              accountService.deductBalance(account.getUserId(), priceDiff);
              buyOrder.setStockbuyPrice(currentPrice);
            } else {
              // 잔고 부족으로 주문 취소
              buyOrder.setStockbuyStatus("CANCELLED");
              // 기존 차감 금액 환불
              accountService.addBalance(account.getUserId(), oldPrice * buyOrder.getStockbuyNum());
              log.warn("예약 주문 취소 (잔고 부족): {}", buyOrder.getStockbuyId());
            }
          } else if (priceDiff < 0) {
            // 가격 하락: 초과 차감 금액 환불
            accountService.addBalance(account.getUserId(), Math.abs(priceDiff));
            buyOrder.setStockbuyPrice(currentPrice);
          }
        }

        stockBuyRepository.save(buyOrder);

        // PENDING 상태로 변경된 주문은 Kafka로 처리 요청
        if ("PENDING".equals(buyOrder.getStockbuyStatus())) {
          publishBuyOrderToKafka(buyOrder.getStockbuyId());
        }
      }

      // 예약된 매도 주문 처리
      List<StockSell> reservedSellOrders = stockSellRepository.findByStocksellStatus("RESERVED");
      for (StockSell sellOrder : reservedSellOrders) {
        sellOrder.setStocksellStatus("PENDING");
        sellOrder.setStocksellChangetime(Timestamp.valueOf(LocalDateTime.now()));

        // 개장 시 실제 시세로 가격 업데이트
        Integer currentPrice = stockPriceService.getCurrentPrice(sellOrder.getHolding().getStock().getStockTicker());
        if (currentPrice != null) {
          sellOrder.setStocksellPrice(currentPrice);
        }

        stockSellRepository.save(sellOrder);

        // PENDING 상태로 변경된 주문은 Kafka로 처리 요청
        if ("PENDING".equals(sellOrder.getStocksellStatus())) {
          publishSellOrderToKafka(sellOrder.getStocksellId());
        }
      }

      log.info("예약 주문 처리 완료: 매수 {}건, 매도 {}건",
          reservedBuyOrders.size(), reservedSellOrders.size());

    } catch (Exception e) {
      log.error("예약 주문 처리 실패", e);
    }
  }

  // ========== 엔터티 생성 메서드 ==========

  /**
   * 매수 주문 엔터티 생성
   */
  private StockBuy createStockBuyEntity(String stockbuyId, Integer price, Integer quantity,
      Account account, Stock stock) {
    StockBuy stockBuy = new StockBuy();
    stockBuy.setStockbuyId(stockbuyId);
    stockBuy.setStockbuyPrice(price);
    stockBuy.setStockbuyNum(quantity);

    // 장내/장외 시간에 따른 상태 설정
    if (stockStatusService.isMarketOpen()) {
      stockBuy.setStockbuyStatus("PENDING");
      log.debug("장내 시간 - 일반 주문으로 접수");
    } else {
      stockBuy.setStockbuyStatus("RESERVED");
      log.debug("장외 시간 - 예약 주문으로 접수");
    }

    stockBuy.setStockbuyCreatetime(Timestamp.valueOf(LocalDateTime.now()));
    stockBuy.setStockbuyChangetime(Timestamp.valueOf(LocalDateTime.now()));
    stockBuy.setAccount(account);
    stockBuy.setStock(stock);

    return stockBuy;
  }

  /**
   * 매도 주문 엔터티 생성
   */
  private StockSell createStockSellEntity(String stocksellId, Integer price, Integer quantity,
      Account account, Holding holding) {
    StockSell stockSell = new StockSell();
    stockSell.setStocksellId(stocksellId);
    stockSell.setStocksellPrice(price);
    stockSell.setStocksellNum(quantity);

    // 장내/장외 시간에 따른 상태 설정
    if (stockStatusService.isMarketOpen()) {
      stockSell.setStocksellStatus("PENDING");
      log.debug("장내 시간 - 일반 주문으로 접수");
    } else {
      stockSell.setStocksellStatus("RESERVED");
      log.debug("장외 시간 - 예약 주문으로 접수");
    }

    stockSell.setStocksellCreatetime(Timestamp.valueOf(LocalDateTime.now()));
    stockSell.setStocksellChangetime(Timestamp.valueOf(LocalDateTime.now()));
    stockSell.setAccount(account);
    stockSell.setHolding(holding);

    return stockSell;
  }

  // ========== 체결 실행 메서드 ==========

  /**
   * 체결 확률 계산 (65~75% 확률)
   */
  private boolean shouldExecuteOrder() {
    double executionRate = 0.65 + random.nextDouble() * 0.1;
    boolean result = random.nextDouble() < executionRate;
    log.debug("체결 확률 계산: executionRate={:.2f}, result={}", executionRate, result);
    return result;
  }

  /**
   * 매수 주문 체결 실행
   */
  private void executeBuyOrder(StockBuy stockBuy) {
    log.info("매수 주문 체결 실행: stockbuyId={}", stockBuy.getStockbuyId());

    try {
      updateHoldingForBuy(stockBuy);
      saveAccountHistoryForBuy(stockBuy.getAccount(),
          stockBuy.getStockbuyPrice() * stockBuy.getStockbuyNum());

      stockBuy.setStockbuyStatus("EXECUTED");
      stockBuy.setStockbuyChangetime(Timestamp.valueOf(LocalDateTime.now()));
      stockBuyRepository.save(stockBuy);

      log.info("매수 주문 체결 완료: stockbuyId={}", stockBuy.getStockbuyId());

    } catch (Exception e) {
      log.error("매수 주문 체결 실패: stockbuyId={}, error={}", stockBuy.getStockbuyId(), e.getMessage());

      // 체결 실패 시 잔고 원복
      Account account = stockBuy.getAccount();
      Integer refundAmount = stockBuy.getStockbuyPrice() * stockBuy.getStockbuyNum();
      accountService.addBalance(account.getUserId(), refundAmount);

      stockBuy.setStockbuyStatus("FAILED");
      stockBuy.setStockbuyChangetime(Timestamp.valueOf(LocalDateTime.now()));
      stockBuyRepository.save(stockBuy);

      throw new RuntimeException("매수 주문 체결 실패: " + e.getMessage());
    }
  }

  /**
   * 매도 주문 체결 실행
   */
  private void executeSellOrder(StockSell stockSell) {
    log.info("매도 주문 체결 실행: stocksellId={}", stockSell.getStocksellId());

    try {
      updateHoldingForSell(stockSell);

      Account account = stockSell.getAccount();
      Integer sellAmount = stockSell.getStocksellPrice() * stockSell.getStocksellNum();

      // AccountService를 통한 잔고 증가
      accountService.addBalance(account.getUserId(), sellAmount);

      saveAccountHistoryForSell(account, sellAmount);

      stockSell.setStocksellStatus("EXECUTED");
      stockSell.setStocksellChangetime(Timestamp.valueOf(LocalDateTime.now()));
      stockSellRepository.save(stockSell);

      log.info("매도 주문 체결 완료: stocksellId={}", stockSell.getStocksellId());

    } catch (Exception e) {
      log.error("매도 주문 체결 실패: stocksellId={}, error={}", stockSell.getStocksellId(),
          e.getMessage());

      stockSell.setStocksellStatus("FAILED");
      stockSell.setStocksellChangetime(Timestamp.valueOf(LocalDateTime.now()));
      stockSellRepository.save(stockSell);

      throw new RuntimeException("매도 주문 체결 실패: " + e.getMessage());
    }
  }

  // ========== 보유 주식 업데이트 ==========

  /**
   * 매수 시 보유 주식 업데이트
   */
  private void updateHoldingForBuy(StockBuy stockBuy) {
    Account account = stockBuy.getAccount();
    Stock stock = stockBuy.getStock();

    Holding existingHolding = getHoldingByAccountAndStock(account, stock.getStockTicker());

    if (existingHolding == null) {
      String holdId = generateId(IdType.HOLDING);
      Holding newHolding = new Holding();
      newHolding.setHoldId(holdId);
      newHolding.setHoldNum(stockBuy.getStockbuyNum());
      newHolding.setHoldBuyprice(stockBuy.getStockbuyPrice());
      newHolding.setHoldCreatetime(Timestamp.valueOf(LocalDateTime.now()));
      newHolding.setHoldChangetime(Timestamp.valueOf(LocalDateTime.now()));
      newHolding.setAccount(account);
      newHolding.setStock(stock);

      holdingRepository.save(newHolding);
      log.debug("신규 보유 주식 생성: holdId={}, quantity={}, price={}",
          holdId, stockBuy.getStockbuyNum(), stockBuy.getStockbuyPrice());
    } else {
      Integer oldQuantity = existingHolding.getHoldNum();
      Integer oldPrice = existingHolding.getHoldBuyprice();
      Integer newQuantity = oldQuantity + stockBuy.getStockbuyNum();
      Integer newPrice = stockBuy.getStockbuyPrice();

      Integer totalAmount = (oldQuantity * oldPrice) + (stockBuy.getStockbuyNum() * newPrice);
      Integer averagePrice = totalAmount / newQuantity;

      existingHolding.setHoldNum(newQuantity);
      existingHolding.setHoldBuyprice(averagePrice);
      existingHolding.setHoldChangetime(Timestamp.valueOf(LocalDateTime.now()));

      holdingRepository.save(existingHolding);
      log.debug("기존 보유 주식 업데이트: holdId={}, oldQty={}, newQty={}, oldPrice={}, newAvgPrice={}",
          existingHolding.getHoldId(), oldQuantity, newQuantity, oldPrice, averagePrice);
    }
  }

  /**
   * 매도 시 보유 주식 업데이트
   */
  private void updateHoldingForSell(StockSell stockSell) {
    Holding holding = stockSell.getHolding();
    Integer remainingQuantity = holding.getHoldNum() - stockSell.getStocksellNum();

    if (remainingQuantity < 0) {
      throw new RuntimeException("보유 수량 부족: 보유=" + holding.getHoldNum() +
          ", 매도요청=" + stockSell.getStocksellNum());
    }

    if (remainingQuantity == 0) {
      holdingRepository.delete(holding);
      log.debug("전량 매도 - 보유 주식 삭제: holdId={}", holding.getHoldId());
    } else {
      holding.setHoldNum(remainingQuantity);
      holding.setHoldChangetime(Timestamp.valueOf(LocalDateTime.now()));
      holdingRepository.save(holding);
      log.debug("부분 매도 - 수량 감소: holdId={}, oldQty={}, newQty={}",
          holding.getHoldId(), holding.getHoldNum() + stockSell.getStocksellNum(),
          remainingQuantity);
    }
  }

  // ========== 거래 내역 저장 ==========

  /**
   * 거래 내역 저장 (매수용)
   */
  private void saveAccountHistoryForBuy(Account account, Integer amount) {
    String historyId = generateId(IdType.ACCOUNT_HISTORY);

    // AccountService를 통해 현재 잔고 조회
    Integer balanceAfter = accountService.getBalance(account.getUserId());
    Integer balanceBefore = balanceAfter + amount;

    AccountHistory history = new AccountHistory();
    history.setAccounthistoryId(historyId);
    history.setTransactionType(AccountHistory.TransactionType.BUY_STOCK);
    history.setTransactionAmount(amount);
    history.setBalanceBefore(balanceBefore);
    history.setBalanceAfter(balanceAfter);
    history.setTransactionTime(Timestamp.valueOf(LocalDateTime.now()));
    history.setAccount(account);

    accountHistoryRepository.save(history);
    log.debug("매수 거래 내역 저장: historyId={}, amount={}, balanceBefore={}, balanceAfter={}",
        historyId, amount, balanceBefore, balanceAfter);
  }

  /**
   * 거래 내역 저장 (매도용)
   */
  private void saveAccountHistoryForSell(Account account, Integer amount) {
    String historyId = generateId(IdType.ACCOUNT_HISTORY);

    // AccountService를 통해 현재 잔고 조회
    Integer balanceAfter = accountService.getBalance(account.getUserId());
    Integer balanceBefore = balanceAfter - amount;

    AccountHistory history = new AccountHistory();
    history.setAccounthistoryId(historyId);
    history.setTransactionType(AccountHistory.TransactionType.SELL_STOCK);
    history.setTransactionAmount(amount);
    history.setBalanceBefore(balanceBefore);
    history.setBalanceAfter(balanceAfter);
    history.setTransactionTime(Timestamp.valueOf(LocalDateTime.now()));
    history.setAccount(account);

    accountHistoryRepository.save(history);
    log.debug("매도 거래 내역 저장: historyId={}, amount={}, balanceBefore={}, balanceAfter={}",
        historyId, amount, balanceBefore, balanceAfter);
  }

  // ========== 입력 검증 메서드 ==========

  /**
   * 매수 주문 입력 검증
   */
  private void validateBuyOrderInput(String userId, String stockTicker, Integer quantity, Integer price) {
    if (userId == null || userId.trim().isEmpty()) {
      throw new IllegalArgumentException("사용자 ID가 필요합니다");
    }

    if (stockTicker == null || stockTicker.trim().isEmpty()) {
      throw new IllegalArgumentException("종목 코드가 필요합니다");
    }

    if (quantity == null || quantity <= 0) {
      throw new IllegalArgumentException("주문 수량은 1주 이상이어야 합니다");
    }

    if (quantity > 10000) {
      throw new IllegalArgumentException("주문 수량은 10,000주를 초과할 수 없습니다");
    }

    if (price != null && price <= 0) {
      throw new IllegalArgumentException("주문 가격은 0원보다 커야 합니다");
    }

    if (price != null && price > 10000000) {
      throw new IllegalArgumentException("주문 가격은 1,000만원을 초과할 수 없습니다");
    }
  }

  /**
   * 매도 주문 입력 검증
   */
  private void validateSellOrderInput(String userId, String stockTicker, Integer quantity, Integer price) {
    if (userId == null || userId.trim().isEmpty()) {
      throw new IllegalArgumentException("사용자 ID가 필요합니다");
    }

    if (stockTicker == null || stockTicker.trim().isEmpty()) {
      throw new IllegalArgumentException("종목 코드가 필요합니다");
    }

    if (quantity == null || quantity <= 0) {
      throw new IllegalArgumentException("주문 수량은 1주 이상이어야 합니다");
    }

    if (quantity > 10000) {
      throw new IllegalArgumentException("주문 수량은 10,000주를 초과할 수 없습니다");
    }

    if (price != null && price <= 0) {
      throw new IllegalArgumentException("주문 가격은 0원보다 커야 합니다");
    }

    if (price != null && price > 10000000) {
      throw new IllegalArgumentException("주문 가격은 1,000만원을 초과할 수 없습니다");
    }
  }

  // ========== 유틸리티 메서드 ==========

  /**
   * 사용자 ID로 계좌 조회 (AccountService 활용)
   */
  private Account getAccountByUserId(String userId) {
    try {
      return accountService.getAccountByUserId(userId);
    } catch (RuntimeException e) {
      // 계좌가 없으면 자동 생성
      log.info("계좌가 없어서 자동 생성: userId={}", userId);
      return accountService.createAccount(userId);
    }
  }

  /**
   * 종목 코드로 종목 정보 조회
   */
  private Stock getStockByTicker(String stockTicker) {
    // TODO: 임시 작성 해제 후 확인 필요
    // 임시 작성 테스트용 더미
    Stock stock = new Stock();
    stock.setStockTicker(stockTicker);
    stock.setStockName("테스트 종목 " + stockTicker);
    stock.setStockSector("테스트 섹터");
    stock.setStockStatus("LISTED");
    return stock;
    // 원래 코드
//    return stockRepository.findById(stockTicker).orElse(null);
  }

  /**
   * 계좌와 종목으로 보유 주식 조회
   */
  private Holding getHoldingByAccountAndStock(Account account, String stockTicker) {
    Optional<Holding> result = holdingRepository.findByAccountAndStock_StockTicker(account, stockTicker);
    return result.orElse(null);
  }

  // ========== ID 생성 ==========

  /**
   * ID 생성 타입 열거형
   */
  public enum IdType {
    STOCK_BUY("BUY"),
    STOCK_SELL("SELL"),
    HOLDING("HOLD"),
    ACCOUNT_HISTORY("HIST");

    private final String prefix;

    IdType(String prefix) {
      this.prefix = prefix;
    }

    public String getPrefix() {
      return prefix;
    }
  }

  /**
   * 고유 ID 생성
   */
  private String generateId(IdType idType) {
    return idType.getPrefix() + "_" + System.currentTimeMillis() + "_" + random.nextInt(1000);
  }
}