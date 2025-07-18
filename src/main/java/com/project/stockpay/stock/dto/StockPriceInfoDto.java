package com.project.stockpay.stock.dto;

import com.project.stockpay.common.websocket.dto.RealTimeOrderbookDto;
import com.project.stockpay.common.websocket.dto.RealTimeStockPriceDto;
import lombok.*;

import java.text.NumberFormat;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockPriceInfoDto {

  // 기본 종목 정보
  private String stockTicker;       // 종목 코드
  private String stockName;         // 종목명
  private String stockSector;       // 섹터
  private String stockStatus;       // 상태 (LISTED, DELISTED 등)

  // 실시간 주가 정보
  private RealTimeStockPriceDto realTimePrice;

  // 실시간 호가 정보
  private RealTimeOrderbookDto orderbook;

  // 시장 상태
  private boolean isMarketOpen;     // 장중/장외 여부
  private long timestamp;           // 조회 시간

  // 편의 메서드들

  /**
   * 현재가 조회 (실시간 데이터가 있으면 사용, 없으면 기본값)
   */
  public Integer getCurrentPrice() {
    if (realTimePrice != null) {
      return realTimePrice.getCurrentPrice();
    }
    return 0;
  }

  /**
   * 전일 대비 금액
   */
  public Integer getChangeAmount() {
    if (realTimePrice != null) {
      return realTimePrice.getChangeAmount();
    }
    return 0;
  }

  /**
   * 전일 대비 등락률
   */
  public Double getChangeRate() {
    if (realTimePrice != null) {
      return realTimePrice.getChangeRate();
    }
    return 0.0;
  }

  /**
   * 거래량
   */
  public Long getVolume() {
    if (realTimePrice != null) {
      return realTimePrice.getVolume();
    }
    return 0L;
  }

  /**
   * 등락 상태
   */
  public String getPriceStatus() {
    if (realTimePrice != null) {
      return realTimePrice.getPriceStatus().name();
    }
    return "UNKNOWN";
  }

  /**
   * 최우선 매수호가
   */
  public Integer getBestBuyPrice() {
    if (orderbook != null) {
      return orderbook.getBestBuyPrice();
    }
    return 0;
  }

  /**
   * 최우선 매도호가
   */
  public Integer getBestSellPrice() {
    if (orderbook != null) {
      return orderbook.getBestSellPrice();
    }
    return 0;
  }

  /**
   * 호가 스프레드
   */
  public Integer getSpread() {
    if (orderbook != null) {
      return orderbook.getSpread();
    }
    return 0;
  }

  /**
   * 실시간 데이터 사용 가능 여부
   */
  public boolean hasRealTimeData() {
    return realTimePrice != null;
  }

  /**
   * 호가 데이터 사용 가능 여부
   */
  public boolean hasOrderbookData() {
    return orderbook != null;
  }

  /**
   * 포맷팅된 현재가
   */
  public String getFormattedCurrentPrice() {
    return NumberFormat.getInstance().format(getCurrentPrice()) + "원";
  }

  /**
   * 포맷팅된 등락률
   */
  public String getFormattedChangeRate() {
    Double rate = getChangeRate();
    String sign = "";

    if (realTimePrice != null) {
      String changeSign = realTimePrice.getChangeSign();
      if ("2".equals(changeSign) || "1".equals(changeSign)) {
        sign = "+";
      } else if ("5".equals(changeSign) || "4".equals(changeSign)) {
        sign = "-";
      }
    }

    return sign + String.format("%.2f%%", Math.abs(rate));
  }

  /**
   * 포맷팅된 거래량
   */
  public String getFormattedVolume() {
    Long vol = getVolume();
    if (vol >= 10000) {
      return String.format("%.1f만주", vol / 10000.0);
    }
    return NumberFormat.getInstance().format(vol) + "주";
  }

  /**
   * 거래 가능 여부 (상장된 종목이고 실시간 데이터가 있는 경우)
   */
  public boolean isTradable() {
    return "LISTED".equals(stockStatus) && hasRealTimeData();
  }

  /**
   * 시장가 매수 추천 가격 (최우선 매도호가)
   */
  public Integer getRecommendedBuyPrice() {
    if (isMarketOpen && hasOrderbookData()) {
      return getBestSellPrice();
    }
    return getCurrentPrice();
  }

  /**
   * 시장가 매도 추천 가격 (최우선 매수호가)
   */
  public Integer getRecommendedSellPrice() {
    if (isMarketOpen && hasOrderbookData()) {
      return getBestBuyPrice();
    }
    return getCurrentPrice();
  }

  /**
   * 데이터 신선도 확인 (5분 이내 데이터만 신선한 것으로 간주)
   */
  public boolean isFreshData() {
    if (realTimePrice == null) {
      return false;
    }

    long dataAge = System.currentTimeMillis() - realTimePrice.getTimestamp();
    return dataAge < 300000; // 5분 = 300초 = 300,000ms
  }

  /**
   * 요약 정보 (디스플레이용)
   */
  public String getSummary() {
    return String.format("%s (%s) : %s (%s)",
        stockName, stockTicker,
        getFormattedCurrentPrice(),
        getFormattedChangeRate());
  }
}