package com.project.stockpay.common.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// 웹소켓 메시지 요청 DTO
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KisWebSocketRequest {
  public static final String TR_STOCK_PRICE = "H0STCNT0";
  public static final String TR_STOCK_ORDERBOOK = "H0STASP0";

  private static final String CUST_TYPE_PERSONAL = "P";
  private static final String CONTENT_TYPE_UTF8 = "utf-8";
  private static final String TYPE_SUBSCRIBE = "1";
  private static final String TYPE_UNSUBSCRIBE = "2";

  @JsonProperty("header")
  private KisWebSocketHeader header;

  @JsonProperty("body")
  private KisWebSocketBody body;

  // 헤더 DTO
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class KisWebSocketHeader {
    @JsonProperty("approval_key")
    private String approvalKey;

    @JsonProperty("custtype")
    private String custType; // P: 개인

    @JsonProperty("tr_type")
    private String trType; // 1: 등록, 2: 해제

    @JsonProperty("content-type")
    private String contentType; // utf-8
  }

  // 바디 DTO
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class KisWebSocketBody {
    @JsonProperty("input")
    private KisWebSocketInput input;
  }

  // 입력 데이터 DTO
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class KisWebSocketInput {
    @JsonProperty("tr_id")
    private String trId; // H0STCNT0, H0STASP0 등

    @JsonProperty("tr_key")
    private String trKey; // 종목코드
  }

  // 주식 체결가 구독
  public static KisWebSocketRequest createStockPriceSubscription(String approvalKey, String stockCode) {
    return KisWebSocketRequest.builder()
        .header(KisWebSocketHeader.builder()
            .approvalKey(approvalKey)
            .custType(CUST_TYPE_PERSONAL)
            .trType(TYPE_SUBSCRIBE)
            .contentType(CONTENT_TYPE_UTF8)
            .build())
        .body(KisWebSocketBody.builder()
            .input(KisWebSocketInput.builder()
                .trId(TR_STOCK_PRICE)
                .trKey(stockCode)
                .build())
            .build())
        .build();
  }

  // 주식 호가 구독
  public static KisWebSocketRequest createStockOrderbookSubscription(String approvalKey, String stockCode) {
    return KisWebSocketRequest.builder()
        .header(KisWebSocketHeader.builder()
            .approvalKey(approvalKey)
            .custType(CUST_TYPE_PERSONAL)
            .trType(TYPE_SUBSCRIBE)
            .contentType(CONTENT_TYPE_UTF8)
            .build())
        .body(KisWebSocketBody.builder()
            .input(KisWebSocketInput.builder()
                .trId(TR_STOCK_ORDERBOOK)
                .trKey(stockCode)
                .build())
            .build())
        .build();
  }

  // 구독 해제
  public static KisWebSocketRequest createUnsubscription(String approvalKey, String trId, String stockCode) {
    return KisWebSocketRequest.builder()
        .header(KisWebSocketHeader.builder()
            .approvalKey(approvalKey)
            .custType(CUST_TYPE_PERSONAL)
            .trType(TYPE_UNSUBSCRIBE)
            .contentType(CONTENT_TYPE_UTF8)
            .build())
        .body(KisWebSocketBody.builder()
            .input(KisWebSocketInput.builder()
                .trId(trId)
                .trKey(stockCode)
                .build())
            .build())
        .build();
  }
}