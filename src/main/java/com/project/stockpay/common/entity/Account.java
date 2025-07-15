package com.project.stockpay.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.sql.*;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor         // JPA용 기본 생성자
@AllArgsConstructor        // Builder용 전체 생성자
@Table(name = "account")
public class Account {

  // 계좌번호
  @Id
  private String accountNum;

  // 계좌상태
  public enum AccountStatus {ACTIVE, INACTIVE, SUSPENDED}
  private AccountStatus accountStatus;
  // 계좌명
  private String accountName;
  // 계좌비밀번호
  private String accountPw;
  // 개설일자
  private Date accountOpendate;
  // 해지일자
  private Date accountClosedate;
  // 출금가능금액
  private Integer accountWithdrawalAmount;
  // 예수금
  private Integer accountAmount;
  // 데이터생성일자
  private Timestamp accountCreatetime;
  // 데이터변경일자
  private Timestamp accountChangetime;

  // @ManyToOne
  @JoinColumn(name = "user_id")
  private String userId;
}
