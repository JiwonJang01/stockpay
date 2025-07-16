package com.project.stockpay.common.websocket.dto;

import lombok.*;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessageDto {
    private String type;        // 메시지 타입 (STOCK_PRICE, ORDERBOOK, ERROR 등)
    private String stockCode;   // 종목코드
    private Object data;        // 실제 데이터
    private Long timestamp;     // 전송 시간

    // 정적 팩토리 메서드들
    public static WebSocketMessageDto stockPrice(String stockCode, RealTimeStockPriceDto data) {
        return WebSocketMessageDto.builder()
                .type("STOCK_PRICE")
                .stockCode(stockCode)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static WebSocketMessageDto orderbook(String stockCode, RealTimeOrderbookDto data) {
        return WebSocketMessageDto.builder()
                .type("ORDERBOOK")
                .stockCode(stockCode)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static WebSocketMessageDto error(String message) {
        return WebSocketMessageDto.builder()
                .type("ERROR")
                .data(Map.of("message", message))
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
