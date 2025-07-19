package com.project.stockpay.common.account.controller;

import com.project.stockpay.common.account.dto.AccountSummaryDto;
import com.project.stockpay.common.account.service.AccountService;
import com.project.stockpay.common.entity.Account;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/test/account")
@RequiredArgsConstructor
@Slf4j
public class AccountController {

  private final AccountService accountService;

  /**
   * 테스트용 계좌 생성
   */
  @PostMapping("/create/{userId}")
  public ResponseEntity<Map<String, Object>> createAccount(@PathVariable String userId) {
    try {
      log.info("계좌 생성 테스트: userId={}", userId);

      Account account = accountService.createAccount(userId);

      Map<String, Object> result = new HashMap<>();
      result.put("status", "SUCCESS");
      result.put("message", "계좌 생성 완료");
      result.put("accountNum", account.getAccountNum());
      result.put("balance", account.getAccountAmount());
      result.put("userId", userId);

      return ResponseEntity.ok(result);

    } catch (Exception e) {
      log.error("계좌 생성 실패: userId={}", userId, e);
      return ResponseEntity.internalServerError()
          .body(Map.of("status", "ERROR", "message", e.getMessage()));
    }
  }

  /**
   * 계좌 조회
   */
  @GetMapping("/{userId}")
  public ResponseEntity<Map<String, Object>> getAccount(@PathVariable String userId) {
    try {
      log.info("계좌 조회: userId={}", userId);

      AccountSummaryDto summary = accountService.getAccountSummary(userId);

      Map<String, Object> result = new HashMap<>();
      result.put("status", "SUCCESS");
      result.put("account", summary);

      return ResponseEntity.ok(result);

    } catch (Exception e) {
      log.error("계좌 조회 실패: userId={}", userId, e);
      return ResponseEntity.internalServerError()
          .body(Map.of("status", "ERROR", "message", e.getMessage()));
    }
  }

  /**
   * 잔고 조회
   */
  @GetMapping("/{userId}/balance")
  public ResponseEntity<Map<String, Object>> getBalance(@PathVariable String userId) {
    try {
      log.info("잔고 조회: userId={}", userId);

      Integer balance = accountService.getBalance(userId);

      Map<String, Object> result = new HashMap<>();
      result.put("status", "SUCCESS");
      result.put("userId", userId);
      result.put("balance", balance);

      return ResponseEntity.ok(result);

    } catch (Exception e) {
      log.error("잔고 조회 실패: userId={}", userId, e);
      return ResponseEntity.internalServerError()
          .body(Map.of("status", "ERROR", "message", e.getMessage()));
    }
  }

  /**
   * 잔고 업데이트 테스트 (임시)
   */
  @PostMapping("/{userId}/balance")
  public ResponseEntity<Map<String, Object>> updateBalance(
      @PathVariable String userId,
      @RequestParam Integer amount) {
    try {
      log.info("잔고 업데이트 테스트: userId={}, amount={}", userId, amount);

      Integer beforeBalance = accountService.getBalance(userId);
      accountService.updateBalance(userId, amount);
      Integer afterBalance = accountService.getBalance(userId);

      Map<String, Object> result = new HashMap<>();
      result.put("status", "SUCCESS");
      result.put("userId", userId);
      result.put("beforeBalance", beforeBalance);
      result.put("afterBalance", afterBalance);
      result.put("amount", amount);

      return ResponseEntity.ok(result);

    } catch (Exception e) {
      log.error("잔고 업데이트 실패: userId={}, amount={}", userId, amount, e);
      return ResponseEntity.internalServerError()
          .body(Map.of("status", "ERROR", "message", e.getMessage()));
    }
  }
}
