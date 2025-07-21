package com.project.stockpay.stock.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SellOrderRequestDto {

  /**
   * 매도 주문 요청 DTO
   */

  private String userId;
  private String stockTicker;
  private Integer quantity;
  private Integer price;

  @Override
  public String toString() {
    return "SellOrderRequest{" +
        "userId='" + userId + '\'' +
        ", stockTicker='" + stockTicker + '\'' +
        ", quantity=" + quantity +
        ", price=" + price +
        '}';
  }
}
