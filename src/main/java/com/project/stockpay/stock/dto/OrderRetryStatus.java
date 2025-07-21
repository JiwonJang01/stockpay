package com.project.stockpay.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRetryStatus {
  private String orderId;
  private int retryCount;
  private int maxRetryCount;
  private Long nextRetryTime;
  private boolean isMaxRetryReached;
}
