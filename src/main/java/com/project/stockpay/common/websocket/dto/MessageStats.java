package com.project.stockpay.common.websocket.dto;

import java.time.LocalDateTime;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
public class MessageStats {

  private long totalMessages;
  private long priceMessages;
  private long orderbookMessages;
  private long errorCount;
  private LocalDateTime lastMessageTime;
  private LocalDateTime timestamp;

  public MessageStats(long totalMessages, long priceMessages, long orderbookMessages,
      long errorCount, LocalDateTime lastMessageTime, LocalDateTime timestamp) {
    this.totalMessages = totalMessages;
    this.priceMessages = priceMessages;
    this.orderbookMessages = orderbookMessages;
    this.errorCount = errorCount;
    this.lastMessageTime = lastMessageTime;
    this.timestamp = timestamp;
  }

}
