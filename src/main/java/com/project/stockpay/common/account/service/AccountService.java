package com.project.stockpay.common.account.service;

import com.project.stockpay.common.account.dto.AccountSummaryDto;
import com.project.stockpay.common.entity.*;
import com.project.stockpay.common.entity.Account.AccountStatus;
import com.project.stockpay.common.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AccountService {

  private final AccountRepository accountRepository;
  private final UserRepository userRepository;

  // 초기 자금
  private static final Integer INITIAL_BALANCE = 1000000; // 100만원

  /**
   * 사용자 계좌 생성 (초기 100만원)
   */
  public Account createAccount(String userId) {
    log.info("계좌 생성 시작: userId={}", userId);

    // 이미 계좌가 있는지 확인
    List<Account> existingAccounts = accountRepository.findByUserId(userId);
    if (!existingAccounts.isEmpty()) {
      Account existingAccount = existingAccounts.get(0);
      log.warn("이미 계좌가 존재함: userId={}, accountNum={}",
          userId, existingAccount.getAccountNum());
      return existingAccount;
    }

    // 사용자 존재 확인 - 현재는 건너뛰고 바로 계좌 생성
//        User user = userRepository.findById(userId)
//                .orElseGet(() -> createTempUser(userId));

    // 계좌번호 생성 - 현재시간 기반
    String accountNum = generateAccountNumber();

    // 계좌 생성
//        Account account = new Account();
//        account.setAccountNum(accountNum);
//        account.setAccountStatus("ACTIVE");
//        account.setAccountType("INVESTMENT");
//        account.setAccountName("모의투자계좌");
//        account.setAccountPw(1234); // 임시 비밀번호
//        account.setAccountOpendate(Date.valueOf(LocalDate.now()));
//        account.setAccountMoney(INITIAL_BALANCE);
//        account.setAccountWithdrawal(INITIAL_BALANCE);
//        account.setAccountCreatetime(Timestamp.valueOf(LocalDateTime.now()));
//        account.setUserId(userId);

    // 이펙티브 자바 1강
    Account account = Account.builder()
        .accountNum(accountNum)
        .userId(userId)  // userId 직접 설정
        .accountStatus(AccountStatus.ACTIVE)
        .accountName("모의투자계좌")
        .accountPw("1234")
        .accountOpendate(Date.valueOf(LocalDate.now()))
        .accountAmount(INITIAL_BALANCE)
        .accountWithdrawalAmount(INITIAL_BALANCE)
        .accountCreatetime(Timestamp.valueOf(LocalDateTime.now()))
        .build();

    Account savedAccount = accountRepository.save(account);
    log.info("계좌 생성 완료: accountNum={}, balance={}",
        savedAccount.getAccountNum(), savedAccount.getAccountAmount());

    return savedAccount;
  }

  /**
   * 잔고 확인
   */
  @Transactional(readOnly = true)
  public Integer getBalance(String userId) {
    Account account = getAccountByUserId(userId);
    return account.getAccountAmount();
  }

  /**
   * 계좌 조회
   */
  @Transactional(readOnly = true)
  public Account getAccountByUserId(String userId) {
    List<Account> accounts = accountRepository.findByUserId(userId);
    if (accounts.isEmpty()) {
      throw new RuntimeException("계좌가 존재하지 않습니다: " + userId);
    }
    return accounts.get(0); // 첫 번째 계좌 반환
  }

  /**
   * 잔고 업데이트 (매수/매도 시 사용)
   */
  public void updateBalance(String userId, Integer amount) {
    log.info("잔고 업데이트: userId={}, amount={}", userId, amount);

    Account account = getAccountByUserId(userId);
    Integer currentBalance = account.getAccountAmount();
    Integer newBalance = currentBalance + amount;

    if (newBalance < 0) {
      throw new RuntimeException("잔고 부족: 현재잔고=" + currentBalance + ", 요청금액=" + amount);
    }

    account.setAccountAmount(newBalance);
    account.setAccountWithdrawalAmount(newBalance); // 출금가능금액도 동일하게 설정
    account.setAccountChangetime(Timestamp.valueOf(LocalDateTime.now()));
    accountRepository.save(account);

    log.info("잔고 업데이트 완료: userId={}, 이전잔고={}, 신규잔고={}",
        userId, currentBalance, newBalance);
  }

  /**
   * 매수 가능 금액 확인
   */
  @Transactional(readOnly = true)
  public boolean canBuy(String userId, Integer requiredAmount) {
    Integer currentBalance = getBalance(userId);
    return currentBalance >= requiredAmount;
  }

  /**
   * 잔고 차감 (매수 시)
   */
  public void deductBalance(String userId, Integer amount) {
    log.info("매수 잔고 차감: userId={}, amount={}", userId, amount);
    updateBalance(userId, -amount); // 음수로 차감
  }

  /**
   * 잔고 증가 (매도 시)
   */
  public void addBalance(String userId, Integer amount) {
    log.info("매도 잔고 증가: userId={}, amount={}", userId, amount);
    updateBalance(userId, amount); // 양수로 증가
  }

  /**
   * 계좌번호 생성 (현재시간 기반)
   */
  private String generateAccountNumber() {
    return "ACC" + System.currentTimeMillis();
  }

  /**
   * 임시 사용자 생성 (로그인 기능 구현 전까지)
   */
  private User createTempUser(String userId) {
    log.info("임시 사용자 생성: userId={}", userId);

//    User tempUser = new User();
//    tempUser.setUserId(userId);
//    tempUser.setUserPw("temp123");
//    tempUser.setUserName("임시사용자" + userId);
//    tempUser.setDataCreateTime(Timestamp.valueOf(LocalDateTime.now()));

    User tempUser = User.builder()
        .userId(userId)
        .userPw("temp123")
        .userName("임시사용자" + userId)
        .dataCreateTime(Timestamp.valueOf(LocalDateTime.now()))
        .build();

    return userRepository.save(tempUser);
  }

  /**
   * 계좌 상세 정보 조회
   */
  @Transactional(readOnly = true)
  public AccountSummaryDto getAccountSummary(String userId) {
    Account account = getAccountByUserId(userId);

//    AccountSummaryDto summary = new AccountSummaryDto();
//    summary.setAccountNum(account.getAccountNum());
//    summary.setUserId(userId);
//    summary.setBalance(new BigDecimal(account.getAccountAmount()));
//    summary.setCreatedAt(account.getAccountCreatetime().toLocalDateTime());

    AccountSummaryDto summary = AccountSummaryDto.builder()
        .accountNum(account.getAccountNum())
        .userId(userId)
        .balance(new BigDecimal(account.getAccountAmount()))
        .createdAt(account.getAccountCreatetime().toLocalDateTime())
        .build();
    return summary;
  }

  /**
   * BigDecimal 타입 잔고 확인 (호환성을 위해)
   */
  @Transactional(readOnly = true)
  public BigDecimal getBalanceAsBigDecimal(String userId) {
    return new BigDecimal(getBalance(userId));
  }

  /**
   * BigDecimal 타입 잔고 업데이트 (호환성을 위해)
   */
  public void updateBalance(String userId, BigDecimal amount) {
    updateBalance(userId, amount.intValue());
  }

  /**
   * BigDecimal 타입 매수 가능 확인 (호환성을 위해)
   */
  @Transactional(readOnly = true)
  public boolean canBuy(String userId, BigDecimal requiredAmount) {
    return canBuy(userId, requiredAmount.intValue());
  }

  /**
   * BigDecimal 타입 잔고 차감 (호환성을 위해)
   */
  public void deductBalance(String userId, BigDecimal amount) {
    deductBalance(userId, amount.intValue());
  }

  /**
   * BigDecimal 타입 잔고 증가 (호환성을 위해)
   */
  public void addBalance(String userId, BigDecimal amount) {
    addBalance(userId, amount.intValue());
  }
}