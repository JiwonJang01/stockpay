package com.project.stockpay.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderMessage {
  private String orderId;
  private String orderType; // "BUY" or "SELL"
  private long timestamp;
  private int retryCount;
  private Long nextRetryTime;
}