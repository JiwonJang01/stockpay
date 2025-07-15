package com.project.stockpay.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.sql.*;

@Entity
@Getter
@Setter
// @Table(name = "account_history")
public class AccountHistory {

  // 잔고변화id
  @Id
  private String accounthistoryId;
  // 거래타입
  public enum TransactionType {BUY_STOCK, SELL_STOCK, BUY_PRODUCT, REFUND}
  private TransactionType transactionType;
  // 관련주문id
  private String orderId;
  // 거래금액
  private Integer transactionAmount;
  // 거래전잔고
  private Integer balanceBefore;
  // 거래후잔고
  private Integer balanceAfter;
  // 거래시간
  private Timestamp transactionTime;
  // 계좌번호 (FK)
  @ManyToOne
  @JoinColumn(name = "account_num")
  private Account account;
}
