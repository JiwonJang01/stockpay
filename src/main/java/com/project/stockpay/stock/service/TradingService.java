package com.project.stockpay.stock.service;

import com.project.stockpay.common.account.service.AccountService;
import com.project.stockpay.common.entity.*;
import com.project.stockpay.common.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TradingService {

  /**
   * 주식 거래 서비스
   * - 매수/매도 주문 접수 및 체결 처리
   * - 확률 기반 체결 시스템 (65~75% 확률, 5회 실패 시 100% 체결)
   * - 보유 주식 관리 및 평균단가 자동 계산
   */

  private final StockBuyRepository stockBuyRepository;
  private final StockSellRepository stockSellRepository;
  private final HoldingRepository holdingRepository;
  private final AccountRepository accountRepository;
  private final StockRepository stockRepository;
  private final AccountHistoryRepository accountHistoryRepository;
  private final AccountService accountService;

  private final Random random = new Random();

  /**
   * 매수 주문 접수
   */
  public String submitBuyOrder(String userId, String stockTicker, Integer quantity, Integer price) {
    log.info("매수 주문 접수 시작: userId={}, stockTicker={}, quantity={}, price={}",
        userId, stockTicker, quantity, price);

    // 1. 총 필요 금액 계산
    Integer totalAmount = price * quantity;
    log.debug("총 필요 금액: {}", totalAmount);

    // 2. 계좌 조회
    Account account = getAccountByUserId(userId);
    if (account == null) {
      throw new RuntimeException("계좌를 찾을 수 없습니다: userId=" + userId);
    }

    // 3. 잔고 확인
    if (account.getAccountAmount() < totalAmount) {
      throw new RuntimeException("잔고 부족: 필요금액=" + totalAmount +
          ", 현재잔고=" + account.getAccountAmount());
    }

    // 4. 종목 정보 확인
    Stock stock = getStockByTicker(stockTicker);
    if (stock == null) {
      throw new RuntimeException("종목을 찾을 수 없습니다: stockTicker=" + stockTicker);
    }

    // 5. 매수 주문 ID 생성
    String stockbuyId = generateId(IdType.STOCK_BUY);

    // 6. 매수 주문 생성
    StockBuy stockBuy = new StockBuy();
    stockBuy.setStockbuyId(stockbuyId);
    stockBuy.setStockbuyPrice(price);
    stockBuy.setStockbuyNum(quantity);
    stockBuy.setStockbuyStatus("PENDING");
    stockBuy.setStockbuyCreatetime(Timestamp.valueOf(LocalDateTime.now()));
    stockBuy.setStockbuyChangetime(Timestamp.valueOf(LocalDateTime.now()));
    stockBuy.setAccount(account);
    stockBuy.setStock(stock);

    stockBuyRepository.save(stockBuy);

    // 7. 잔고 즉시 차감 (주문 접수와 동시에)
    account.setAccountAmount(account.getAccountAmount() - totalAmount);
    account.setAccountChangetime(Timestamp.valueOf(LocalDateTime.now()));
    accountRepository.save(account);

    log.info("매수 주문 접수 완료: stockbuyId={}, totalAmount={}", stockbuyId, totalAmount);
    return stockbuyId;
  }

  /**
   * 매도 주문 접수
   */
  public String submitSellOrder(String userId, String stockTicker, Integer quantity,
      Integer price) {
    log.info("매도 주문 접수 시작: userId={}, stockTicker={}, quantity={}, price={}",
        userId, stockTicker, quantity, price);

    // 1. 계좌 조회
    Account account = getAccountByUserId(userId);
    if (account == null) {
      throw new RuntimeException("계좌를 찾을 수 없습니다: userId=" + userId);
    }

    // 2. 보유 주식 조회
    Holding holding = getHoldingByAccountAndStock(account, stockTicker);
    if (holding == null) {
      throw new RuntimeException("보유 주식이 없습니다: stockTicker=" + stockTicker);
    }

    // 3. 보유 수량 확인
    if (holding.getHoldNum() < quantity) {
      throw new RuntimeException("보유 수량 부족: 보유수량=" + holding.getHoldNum() +
          ", 매도요청=" + quantity);
    }

    // 4. 종목 정보 확인
    Stock stock = getStockByTicker(stockTicker);
    if (stock == null) {
      throw new RuntimeException("종목을 찾을 수 없습니다: stockTicker=" + stockTicker);
    }

    // 5. 매도 주문 ID 생성
    String stocksellId = generateId(IdType.STOCK_SELL);

    // 6. 매도 주문 생성
    StockSell stockSell = new StockSell();
    stockSell.setStocksellId(stocksellId);
    stockSell.setStocksellPrice(price);
    stockSell.setStocksellNum(quantity);
    stockSell.setStocksellStatus("PENDING"); // 대기중
    stockSell.setStocksellCreatetime(Timestamp.valueOf(LocalDateTime.now()));
    stockSell.setStocksellChangetime(Timestamp.valueOf(LocalDateTime.now()));
    stockSell.setAccount(account);
    stockSell.setHolding(holding);

    stockSellRepository.save(stockSell);

    log.info("매도 주문 접수 완료: stocksellId={}", stocksellId);
    return stocksellId;
  }

  /**
   * 매수 주문 체결 처리 (확률 기반)
   */
  public void processBuyOrder(String stockbuyId) {
    log.info("매수 주문 체결 처리 시작: stockbuyId={}", stockbuyId);

    // 1. 매수 주문 조회
    StockBuy stockBuy = stockBuyRepository.findById(stockbuyId)
        .orElseThrow(() -> new RuntimeException("매수 주문을 찾을 수 없습니다: " + stockbuyId));

    // 2. 주문 상태 확인
    if (!"PENDING".equals(stockBuy.getStockbuyStatus())) {
      log.warn("이미 처리된 주문: stockbuyId={}, status={}", stockbuyId, stockBuy.getStockbuyStatus());
      return;
    }

    // 3. 체결 확률 계산 (65~75% 확률)
    boolean isExecuted = shouldExecuteOrder();

    if (isExecuted) {
      // 체결 성공
      executeBuyOrder(stockBuy);
    } else {
      // 체결 실패 - 재시도 대기 상태 유지
      log.info("매수 주문 체결 실패 - 재시도 대기: stockbuyId={}", stockbuyId);
    }
  }

  /**
   * 매도 주문 체결 처리 (확률 기반)
   */
  public void processSellOrder(String stocksellId) {
    log.info("매도 주문 체결 처리 시작: stocksellId={}", stocksellId);

    // 1. 매도 주문 조회
    StockSell stockSell = stockSellRepository.findById(stocksellId)
        .orElseThrow(() -> new RuntimeException("매도 주문을 찾을 수 없습니다: " + stocksellId));

    // 2. 주문 상태 확인
    if (!"PENDING".equals(stockSell.getStocksellStatus())) {
      log.warn("이미 처리된 주문: stocksellId={}, status={}", stocksellId, stockSell.getStocksellStatus());
      return;
    }

    // 3. 체결 확률 계산 (65~75% 확률)
    boolean isExecuted = shouldExecuteOrder();

    if (isExecuted) {
      // 체결 성공
      executeSellOrder(stockSell);
    } else {
      // 체결 실패 - 재시도 대기 상태 유지
      log.info("매도 주문 체결 실패 - 재시도 대기: stocksellId={}", stocksellId);
    }
  }

  /**
   * 체결 확률 계산 (65~75% 확률)
   */
  private boolean shouldExecuteOrder() {
    // 65~75% 확률 계산 (0.65 ~ 0.75)
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
      // 1. 보유 주식 업데이트 (신규 생성 또는 기존 수량 증가)
      updateHoldingForBuy(stockBuy);

      // 2. 거래 내역 저장 (매수 시에는 잔고가 이미 차감된 상태이므로 체결 시점에 기록)
      saveAccountHistoryForBuy(stockBuy.getAccount(),
          stockBuy.getStockbuyPrice() * stockBuy.getStockbuyNum());

      // 3. 주문 상태 업데이트
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

      // 주문 상태를 실패로 변경
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
      // 1. 보유 주식 수량 감소
      updateHoldingForSell(stockSell);

      // 2. 계좌 잔고 증가
      Account account = stockSell.getAccount();
      Integer sellAmount = stockSell.getStocksellPrice() * stockSell.getStocksellNum();
      account.setAccountAmount(account.getAccountAmount() + sellAmount);
      account.setAccountChangetime(Timestamp.valueOf(LocalDateTime.now()));
      accountRepository.save(account);

      // 3. 거래 내역 저장
      saveAccountHistoryForSell(account, sellAmount);

      // 4. 주문 상태 업데이트
      stockSell.setStocksellStatus("EXECUTED");
      stockSell.setStocksellChangetime(Timestamp.valueOf(LocalDateTime.now()));
      stockSellRepository.save(stockSell);

      log.info("매도 주문 체결 완료: stocksellId={}", stockSell.getStocksellId());

    } catch (Exception e) {
      log.error("매도 주문 체결 실패: stocksellId={}, error={}", stockSell.getStocksellId(),
          e.getMessage());

      // 주문 상태를 실패로 변경
      stockSell.setStocksellStatus("FAILED");
      stockSell.setStocksellChangetime(Timestamp.valueOf(LocalDateTime.now()));
      stockSellRepository.save(stockSell);

      throw new RuntimeException("매도 주문 체결 실패: " + e.getMessage());
    }
  }

  /**
   * 매수 시 보유 주식 업데이트
   */
  private void updateHoldingForBuy(StockBuy stockBuy) {
    Account account = stockBuy.getAccount();
    Stock stock = stockBuy.getStock();

    // 기존 보유 주식 조회
    Holding existingHolding = getHoldingByAccountAndStock(account, stock.getStockTicker());

    if (existingHolding == null) {
      // 신규 보유 주식 생성
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
      // 기존 보유 주식 수량 증가 및 평균단가 재계산
      Integer oldQuantity = existingHolding.getHoldNum();
      Integer oldPrice = existingHolding.getHoldBuyprice();
      Integer newQuantity = oldQuantity + stockBuy.getStockbuyNum();
      Integer newPrice = stockBuy.getStockbuyPrice();

      // 평균단가 계산: (기존총액 + 신규총액) / 총수량
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

    // 매도 후 남은 수량 계산
    Integer remainingQuantity = holding.getHoldNum() - stockSell.getStocksellNum();

    if (remainingQuantity < 0) {
      throw new RuntimeException("보유 수량 부족: 보유=" + holding.getHoldNum() +
          ", 매도요청=" + stockSell.getStocksellNum());
    }

    if (remainingQuantity == 0) {
      // 전량 매도 시 보유 주식 삭제
      holdingRepository.delete(holding);
      log.debug("전량 매도 - 보유 주식 삭제: holdId={}", holding.getHoldId());

    } else {
      // 부분 매도 시 수량만 감소
      holding.setHoldNum(remainingQuantity);
      holding.setHoldChangetime(Timestamp.valueOf(LocalDateTime.now()));
      holdingRepository.save(holding);
      log.debug("부분 매도 - 수량 감소: holdId={}, oldQty={}, newQty={}",
          holding.getHoldId(), holding.getHoldNum() + stockSell.getStocksellNum(),
          remainingQuantity);
    }
  }

  /**
   * 거래 내역 저장 (매수용 - 잔고가 이미 차감된 상태)
   */
  private void saveAccountHistoryForBuy(Account account, Integer amount) {
    String historyId = generateId(IdType.ACCOUNT_HISTORY);

    // 매수는 주문 접수 시에 이미 잔고가 차감되었으므로, 현재 잔고가 거래 후 잔고
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
   * 거래 내역 저장 (매도용 - 잔고 증가)
   */
  private void saveAccountHistoryForSell(Account account, Integer amount) {
    String historyId = generateId(IdType.ACCOUNT_HISTORY);

    // 매도는 잔고 증가 후의 상태
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

  /**
   * 사용자 ID로 계좌 조회
   */
  private Account getAccountByUserId(String userId) {
    List<Account> accounts = accountRepository.findByUserId(userId);
    return accounts.isEmpty() ? null : accounts.get(0);
  }

  /**
   * 종목 티커로 주식 정보 조회
   */
  private Stock getStockByTicker(String stockTicker) {
    return stockRepository.findById(stockTicker)
        .orElse(null);
  }

  /**
   * 계좌와 종목으로 보유 주식 조회
   */
  private Holding getHoldingByAccountAndStock(Account account, String stockTicker) {
    Optional<Holding> result = holdingRepository.findByAccountAndStock_StockTicker(account,
        stockTicker);
    return result.orElse(null);
  }

  /**
   * 대기 중인 매수 주문 조회
   */
  @Transactional(readOnly = true)
  public List<StockBuy> getPendingBuyOrders(String userId) {
    Account account = getAccountByUserId(userId);
    if (account == null) {
      throw new RuntimeException("계좌를 찾을 수 없습니다: userId=" + userId);
    }

    return stockBuyRepository.findByAccountAndStockbuyStatus(account, "PENDING");
  }

  /**
   * 대기 중인 매도 주문 조회
   */
  @Transactional(readOnly = true)
  public List<StockSell> getPendingSellOrders(String userId) {
    Account account = getAccountByUserId(userId);
    if (account == null) {
      throw new RuntimeException("계좌를 찾을 수 없습니다: userId=" + userId);
    }

    return stockSellRepository.findByAccountAndStocksellStatus(account, "PENDING");
  }

  /**
   * 테스트용 매수 주문 접수 및 즉시 체결 처리
   */
  public String submitAndProcessBuyOrder(String userId, String stockTicker, Integer quantity,
      Integer price) {
    // 1. 주문 접수
    String stockbuyId = submitBuyOrder(userId, stockTicker, quantity, price);

    // 2. 즉시 체결 처리 (테스트용)
    processBuyOrder(stockbuyId);

    return stockbuyId;
  }

  /**
   * 테스트용 매도 주문 접수 및 즉시 체결 처리
   */
  public String submitAndProcessSellOrder(String userId, String stockTicker, Integer quantity,
      Integer price) {
    // 1. 주문 접수
    String stocksellId = submitSellOrder(userId, stockTicker, quantity, price);

    // 2. 즉시 체결 처리 (테스트용)
    processSellOrder(stocksellId);

    return stocksellId;
  }

  /**
   * ID 생성 메서드
   */
  public enum IdType {
    // 거래 타입 구분
    STOCK_BUY("BUY"),
    STOCK_SELL("SELL"),
    HOLDING("HOLD"),
    ACCOUNT_HISTORY("HIST");

    private final String prefix;

    // perfix 고정
    IdType(String prefix) {
      this.prefix = prefix;
    }

    public String getPrefix() {
      return prefix;
    }
  }

  // 거래 시간 기반
  private String generateId(IdType idType) {
    return idType.getPrefix() + "_" + System.currentTimeMillis() + "_" + random.nextInt(1000);
  }
}