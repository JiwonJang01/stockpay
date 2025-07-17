package com.project.stockpay.common.account.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class AccountSummaryDto {

  private String accountNum;
  private String userId;
  private BigDecimal balance;
  private LocalDateTime createdAt;
}
