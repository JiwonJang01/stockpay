package com.project.stockpay.common.websocket.dto;

import lombok.*;

import java.util.Arrays;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealTimeOrderbookDto {

  private String stockCode;          // 종목코드
  private Integer[] sellPrices;      // 매도호가 1~10
  private Integer[] buyPrices;       // 매수호가 1~10
  private Long[] sellVolumes;        // 매도잔량 1~10
  private Long[] buyVolumes;         // 매수잔량 1~10
  private Long timestamp;            // 수신시간

  // 최우선 매도호가 (매도 1호가)
  public Integer getBestSellPrice() {
    return sellPrices != null && sellPrices.length > 0 ? sellPrices[0] : 0;
  }

  // 최우선 매수호가 (매수 1호가)
  public Integer getBestBuyPrice() {
    return buyPrices != null && buyPrices.length > 0 ? buyPrices[0] : 0;
  }

  // 스프레드 (매도 1호가 - 매수 1호가)
  public Integer getSpread() {
    return getBestSellPrice() - getBestBuyPrice();
  }

  // 총 매도 잔량
  public Long getTotalSellVolume() {
    return sellVolumes != null ?
        Arrays.stream(sellVolumes).mapToLong(Long::longValue).sum() : 0L;
  }

  // 총 매수 잔량
  public Long getTotalBuyVolume() {
    return buyVolumes != null ?
        Arrays.stream(buyVolumes).mapToLong(Long::longValue).sum() : 0L;
  }

  // 호가 비율 (매수잔량 / (매수잔량 + 매도잔량))
  public Double getBuyRatio() {
    long totalBuy = getTotalBuyVolume();
    long totalSell = getTotalSellVolume();
    long total = totalBuy + totalSell;

    return total > 0 ? (double) totalBuy / total * 100 : 0.0;
  }
}
