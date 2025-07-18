package com.project.stockpay.stock.service;

import com.project.stockpay.common.account.service.AccountService;
import com.project.stockpay.common.entity.*;
import com.project.stockpay.common.repository.*;
import com.project.stockpay.common.websocket.dto.RealTimeStockPriceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * 주식 거래 서비스
 * - 실시간 주가 데이터 기반 거래
 * - 장내/장외 시간 판단
 * - 확률 기반 체결 시스템
 * - 예약 주문 지원
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TradingService {

  private final StockBuyRepository stockBuyRepository;
  private final StockSellRepository stockSellRepository;
  private final HoldingRepository holdingRepository;
  private final AccountRepository accountRepository;
  private final StockRepository stockRepository;
  private final AccountHistoryRepository accountHistoryRepository;
  private final AccountService accountService;
  private final RedisTemplate<String, Object> redisTemplate;

  private final Random random = new Random();

  // 거래 시간 상수
  private static final LocalTime MARKET_OPEN = LocalTime.of(9, 0);
  private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 30);

  /**
   * 현재 시간 기반 장내/장외 판단
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
   * 실시간 주가 데이터 조회
   */
  public RealTimeStockPriceDto getRealTimeStockPrice(String stockTicker) {
    try {
      String cacheKey = "realtime:stock:" + stockTicker;
      RealTimeStockPriceDto priceData = (RealTimeStockPriceDto) redisTemplate.opsForValue()
          .get(cacheKey);

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
   * 현재가 기준 매수 주문 접수
   */
  public String submitBuyOrderAtMarketPrice(String userId, String stockTicker, Integer quantity) {
    log.info("시장가 매수 주문 접수: userId={}, stockTicker={}, quantity={}", userId, stockTicker, quantity);

    // 1. 현재가 조회
    Integer currentPrice = getCurrentPrice(stockTicker);
    if (currentPrice == null) {
      throw new RuntimeException("현재가 조회 실패: " + stockTicker);
    }

    // 2. 매수 주문 접수
    return submitBuyOrder(userId, stockTicker, quantity, currentPrice);
  }

  /**
   * 현재가 기준 매도 주문 접수
   */
  public String submitSellOrderAtMarketPrice(String userId, String stockTicker, Integer quantity) {
    log.info("시장가 매도 주문 접수: userId={}, stockTicker={}, quantity={}", userId, stockTicker, quantity);

    // 1. 현재가 조회
    Integer currentPrice = getCurrentPrice(stockTicker);
    if (currentPrice == null) {
      throw new RuntimeException("현재가 조회 실패: " + stockTicker);
    }

    // 2. 매도 주문 접수
    return submitSellOrder(userId, stockTicker, quantity, currentPrice);
  }

  /**
   * 매수 주문 접수 (개선된 버전)
   */
  public String submitBuyOrder(String userId, String stockTicker, Integer quantity, Integer price) {
    log.info("매수 주문 접수 시작: userId={}, stockTicker={}, quantity={}, price={}",
        userId, stockTicker, quantity, price);

    // 1. 가격이 null이면 현재가로 설정
    if (price == null) {
      price = getCurrentPrice(stockTicker);
      if (price == null) {
        throw new RuntimeException("가격 정보를 가져올 수 없습니다: " + stockTicker);
      }
    }

    // 2. 총 필요 금액 계산
    Integer totalAmount = price * quantity;

    // 3. 계좌 조회
    Account account = getAccountByUserId(userId);
    if (account == null) {
      throw new RuntimeException("계좌를 찾을 수 없습니다: userId=" + userId);
    }

    // 4. 잔고 확인
    if (account.getAccountAmount() < totalAmount) {
      throw new RuntimeException("잔고 부족: 필요금액=" + totalAmount +
          ", 현재잔고=" + account.getAccountAmount());
    }

    // 5. 종목 정보 확인
    Stock stock = getStockByTicker(stockTicker);
    if (stock == null) {
      throw new RuntimeException("종목을 찾을 수 없습니다: stockTicker=" + stockTicker);
    }

    // 6. 매수 주문 생성
    String stockbuyId = generateId(IdType.STOCK_BUY);
    StockBuy stockBuy = new StockBuy();
    stockBuy.setStockbuyId(stockbuyId);
    stockBuy.setStockbuyPrice(price);
    stockBuy.setStockbuyNum(quantity);

    // 7. 장내/장외 시간에 따른 상태 설정
    if (isMarketOpen()) {
      stockBuy.setStockbuyStatus("PENDING");
      log.info("장내 시간 - 일반 주문으로 접수");
    } else {
      stockBuy.setStockbuyStatus("RESERVED");
      log.info("장외 시간 - 예약 주문으로 접수");
    }

    stockBuy.setStockbuyCreatetime(Timestamp.valueOf(LocalDateTime.now()));
    stockBuy.setStockbuyChangetime(Timestamp.valueOf(LocalDateTime.now()));
    stockBuy.setAccount(account);
    stockBuy.setStock(stock);

    stockBuyRepository.save(stockBuy);

    // 8. 잔고 차감 (예약 주문도 미리 차감)
    account.setAccountAmount(account.getAccountAmount() - totalAmount);
    account.setAccountChangetime(Timestamp.valueOf(LocalDateTime.now()));
    accountRepository.save(account);

    log.info("매수 주문 접수 완료: stockbuyId={}, status={}, totalAmount={}",
        stockbuyId, stockBuy.getStockbuyStatus(), totalAmount);
    return stockbuyId;
  }

  /**
   * 매도 주문 접수 (개선된 버전)
   */
  public String submitSellOrder(String userId, String stockTicker, Integer quantity,
      Integer price) {
    log.info("매도 주문 접수 시작: userId={}, stockTicker={}, quantity={}, price={}",
        userId, stockTicker, quantity, price);

    // 1. 가격이 null이면 현재가로 설정
    if (price == null) {
      price = getCurrentPrice(stockTicker);
      if (price == null) {
        throw new RuntimeException("가격 정보를 가져올 수 없습니다: " + stockTicker);
      }
    }

    // 2. 계좌 조회
    Account account = getAccountByUserId(userId);
    if (account == null) {
      throw new RuntimeException("계좌를 찾을 수 없습니다: userId=" + userId);
    }

    // 3. 보유 주식 조회
    Holding holding = getHoldingByAccountAndStock(account, stockTicker);
    if (holding == null) {
      throw new RuntimeException("보유 주식이 없습니다: stockTicker=" + stockTicker);
    }

    // 4. 보유 수량 확인
    if (holding.getHoldNum() < quantity) {
      throw new RuntimeException("보유 수량 부족: 보유수량=" + holding.getHoldNum() +
          ", 매도요청=" + quantity);
    }

    // 5. 종목 정보 확인
    Stock stock = getStockByTicker(stockTicker);
    if (stock == null) {
      throw new RuntimeException("종목을 찾을 수 없습니다: stockTicker=" + stockTicker);
    }

    // 6. 매도 주문 생성
    String stocksellId = generateId(IdType.STOCK_SELL);
    StockSell stockSell = new StockSell();
    stockSell.setStocksellId(stocksellId);
    stockSell.setStocksellPrice(price);
    stockSell.setStocksellNum(quantity);

    // 7. 장내/장외 시간에 따른 상태 설정
    if (isMarketOpen()) {
      stockSell.setStocksellStatus("PENDING");
      log.info("장내 시간 - 일반 주문으로 접수");
    } else {
      stockSell.setStocksellStatus("RESERVED");
      log.info("장외 시간 - 예약 주문으로 접수");
    }

    stockSell.setStocksellCreatetime(Timestamp.valueOf(LocalDateTime.now()));
    stockSell.setStocksellChangetime(Timestamp.valueOf(LocalDateTime.now()));
    stockSell.setAccount(account);
    stockSell.setHolding(holding);

    stockSellRepository.save(stockSell);

    log.info("매도 주문 접수 완료: stocksellId={}, status={}", stocksellId, stockSell.getStocksellStatus());
    return stocksellId;
  }

  /**
   * 현재가 조회 (장내/장외 시간 고려)
   */
  private Integer getCurrentPrice(String stockTicker) {
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
   * 전일 종가 조회 (DB 또는 외부 API)
   */
  private Integer getPreviousClosePrice(String stockTicker) {
    try {
      // TODO: 실제 구현 시 DB에서 전일 종가 조회 또는 외부 API 호출
      // 현재는 임시로 실시간 데이터가 있으면 그것을 사용
      RealTimeStockPriceDto realTimePrice = getRealTimeStockPrice(stockTicker);
      if (realTimePrice != null) {
        return realTimePrice.getCurrentPrice();
      }

      // 임시 기본값 (실제로는 DB에서 조회해야 함)
      switch (stockTicker) {
        case "005930":
          return 70000; // 삼성전자 임시값
        case "035420":
          return 200000; // NAVER 임시값
        case "000660":
          return 120000; // SK하이닉스 임시값
        default:
          return 50000; // 기본값
      }
    } catch (Exception e) {
      log.error("전일 종가 조회 실패: {}", stockTicker, e);
      return 50000; // 기본값
    }
  }

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
        Integer currentPrice = getCurrentPrice(buyOrder.getStock().getStockTicker());
        if (currentPrice != null) {
          Integer oldPrice = buyOrder.getStockbuyPrice();
          Integer priceDiff = (currentPrice - oldPrice) * buyOrder.getStockbuyNum();

          // 가격 차이만큼 잔고 조정
          Account account = buyOrder.getAccount();
          if (priceDiff > 0) {
            // 가격 상승: 추가 차감 필요
            if (account.getAccountAmount() >= priceDiff) {
              account.setAccountAmount(account.getAccountAmount() - priceDiff);
              buyOrder.setStockbuyPrice(currentPrice);
            } else {
              // 잔고 부족으로 주문 취소
              buyOrder.setStockbuyStatus("CANCELLED");
              // 기존 차감 금액 환불
              account.setAccountAmount(
                  account.getAccountAmount() + oldPrice * buyOrder.getStockbuyNum());
              log.warn("예약 주문 취소 (잔고 부족): {}", buyOrder.getStockbuyId());
            }
          } else if (priceDiff < 0) {
            // 가격 하락: 초과 차감 금액 환불
            account.setAccountAmount(account.getAccountAmount() + Math.abs(priceDiff));
            buyOrder.setStockbuyPrice(currentPrice);
          }

          accountRepository.save(account);
        }

        stockBuyRepository.save(buyOrder);
      }

      // 예약된 매도 주문 처리
      List<StockSell> reservedSellOrders = stockSellRepository.findByStocksellStatus("RESERVED");
      for (StockSell sellOrder : reservedSellOrders) {
        sellOrder.setStocksellStatus("PENDING");
        sellOrder.setStocksellChangetime(Timestamp.valueOf(LocalDateTime.now()));

        // 개장 시 실제 시세로 가격 업데이트
        Integer currentPrice = getCurrentPrice(sellOrder.getHolding().getStock().getStockTicker());
        if (currentPrice != null) {
          sellOrder.setStocksellPrice(currentPrice);
        }

        stockSellRepository.save(sellOrder);
      }

      log.info("예약 주문 처리 완료: 매수 {}건, 매도 {}건",
          reservedBuyOrders.size(), reservedSellOrders.size());

    } catch (Exception e) {
      log.error("예약 주문 처리 실패", e);
    }
  }

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
   * 체결 확률 계산 (65~75% 확률)
   */
  private boolean shouldExecuteOrder() {
    double executionRate = 0.65 + random.nextDouble() * 0.1;
    boolean result = random.nextDouble() < executionRate;
    log.debug("체결 확률 계산: executionRate={:.2f}, result={}", executionRate, result);
    return result;
  }

  // 기존 메서드들은 그대로 유지 (executeBuyOrder, executeSellOrder, updateHoldingForBuy, etc.)

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
      account.setAccountAmount(account.getAccountAmount() + refundAmount);
      account.setAccountChangetime(Timestamp.valueOf(LocalDateTime.now()));
      accountRepository.save(account);

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
      account.setAccountAmount(account.getAccountAmount() + sellAmount);
      account.setAccountChangetime(Timestamp.valueOf(LocalDateTime.now()));
      accountRepository.save(account);

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

  // 기존 유틸리티 메서드들 (updateHoldingForBuy, updateHoldingForSell, saveAccountHistoryForBuy, etc.)은 그대로 유지

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

  /**
   * 거래 내역 저장 (매수용)
   */
  private void saveAccountHistoryForBuy(Account account, Integer amount) {
    String historyId = generateId(IdType.ACCOUNT_HISTORY);
    Integer balanceAfter = account.getAccountAmount();
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
    Integer balanceAfter = account.getAccountAmount();
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

  // 기존 유틸리티 메서드들
  private Account getAccountByUserId(String userId) {
    List<Account> accounts = accountRepository.findByUserId(userId);
    return accounts.isEmpty() ? null : accounts.get(0);
  }

  private Stock getStockByTicker(String stockTicker) {
    return stockRepository.findById(stockTicker).orElse(null);
  }

  private Holding getHoldingByAccountAndStock(Account account, String stockTicker) {
    Optional<Holding> result = holdingRepository.findByAccountAndStock_StockTicker(account,
        stockTicker);
    return result.orElse(null);
  }

  @Transactional(readOnly = true)
  public List<StockBuy> getPendingBuyOrders(String userId) {
    Account account = getAccountByUserId(userId);
    if (account == null) {
      throw new RuntimeException("계좌를 찾을 수 없습니다: userId=" + userId);
    }
    return stockBuyRepository.findByAccountAndStockbuyStatus(account, "PENDING");
  }

  @Transactional(readOnly = true)
  public List<StockSell> getPendingSellOrders(String userId) {
    Account account = getAccountByUserId(userId);
    if (account == null) {
      throw new RuntimeException("계좌를 찾을 수 없습니다: userId=" + userId);
    }
    return stockSellRepository.findByAccountAndStocksellStatus(account, "PENDING");
  }

  @Transactional(readOnly = true)
  public List<StockBuy> getReservedBuyOrders(String userId) {
    Account account = getAccountByUserId(userId);
    if (account == null) {
      throw new RuntimeException("계좌를 찾을 수 없습니다: userId=" + userId);
    }
    return stockBuyRepository.findByAccountAndStockbuyStatus(account, "RESERVED");
  }

  @Transactional(readOnly = true)
  public List<StockSell> getReservedSellOrders(String userId) {
    Account account = getAccountByUserId(userId);
    if (account == null) {
      throw new RuntimeException("계좌를 찾을 수 없습니다: userId=" + userId);
    }
    return stockSellRepository.findByAccountAndStocksellStatus(account, "RESERVED");
  }

  /**
   * 테스트용 매수 주문 접수 및 즉시 체결 처리
   */
  public String submitAndProcessBuyOrder(String userId, String stockTicker, Integer quantity,
      Integer price) {
    String stockbuyId = submitBuyOrder(userId, stockTicker, quantity, price);
    processBuyOrder(stockbuyId);
    return stockbuyId;
  }

  /**
   * 테스트용 매도 주문 접수 및 즉시 체결 처리
   */
  public String submitAndProcessSellOrder(String userId, String stockTicker, Integer quantity,
      Integer price) {
    String stocksellId = submitSellOrder(userId, stockTicker, quantity, price);
    processSellOrder(stocksellId);
    return stocksellId;
  }

  /**
   * ID 생성 메서드
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

  private String generateId(IdType idType) {
    return idType.getPrefix() + "_" + System.currentTimeMillis() + "_" + random.nextInt(1000);
  }
}