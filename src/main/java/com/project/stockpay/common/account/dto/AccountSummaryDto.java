package com.project.stockpay.common.account.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AccountSummaryDto {

  private String accountNum;
  private String userId;
  private BigDecimal balance;
  private LocalDateTime createdAt;
}
