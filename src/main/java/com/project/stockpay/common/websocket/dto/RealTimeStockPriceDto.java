package com.project.stockpay.common.websocket.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.*;

import java.text.NumberFormat;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealTimeStockPriceDto {
    private String stockCode;        // 종목코드
    private LocalDateTime tradeTime;        // 체결시간 (HHMMSS)
    private Integer currentPrice;    // 현재가
    private String changeSign;       // 전일대비부호 (1:상한, 2:상승, 3:보합, 4:하한, 5:하락)
    private Integer changeAmount;    // 전일대비
    private Double changeRate;       // 전일대비율
    private Long volume;            // 누적거래량
    private Long tradingValue;      // 누적거래대금
    private Long timestamp;         // 수신시간

    // 등락 상태 반환
    public enum PriceStatus {
        UP_LIMIT, UP, FLAT, DOWN_LIMIT, DOWN, UNKNOWN
    }
    public PriceStatus getPriceStatus() {
        switch (changeSign) {
            case "1": return PriceStatus.UP_LIMIT;
            case "2": return PriceStatus.UP;
            case "3": return PriceStatus.FLAT;
            case "4": return PriceStatus.DOWN_LIMIT;
            case "5": return PriceStatus.DOWN;
            default: return PriceStatus.UNKNOWN;
        }
    }

    // 체결시간 포맷팅
    public String getFormattedTradeTime() {
        if (tradeTime != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        }
        return null;
    }

    // 가격 포맷팅
    public String getFormattedPrice() {
        return NumberFormat.getInstance().format(currentPrice) + "원";
    }

    // 등락률 포맷팅
    public String getFormattedChangeRate() {
        String sign = "";
        if ("2".equals(changeSign) || "1".equals(changeSign)) {
            sign = "+";
        } else if ("5".equals(changeSign) || "4".equals(changeSign)) {
            sign = "-";
        }
        return sign + String.format("%.2f%%", Math.abs(changeRate));
    }

    // 거래량 포맷팅
    public String getFormattedVolume() {
        if (volume >= 10000) {
            return String.format("%.1f만주", volume / 10000.0);
        }
        return NumberFormat.getInstance().format(volume) + "주";
    }
}
