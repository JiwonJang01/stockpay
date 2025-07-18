package com.project.stockpay.common.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KisApiResponse<T> {

  // 헤더 정보
  @JsonProperty("header")
  private KisResponseHeader header;

  // 바디 정보
  @JsonProperty("body")
  private T body;

  // 헤더 DTO
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class KisResponseHeader {
    @JsonProperty("tr_id")
    private String trId;

    @JsonProperty("tr_key")
    private String trKey;

    @JsonProperty("encrypt")
    private String encrypt;

    @JsonProperty("approval_key")
    private String approvalKey;
  }

  // 바디 DTO (일반적인 응답)
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class KisResponseBody {
    @JsonProperty("rt_cd")
    private String rtCd;  // 응답코드

    @JsonProperty("msg_cd")
    private String msgCd; // 메시지코드

    @JsonProperty("msg1")
    private String msg1;  // 메시지

    @JsonProperty("output")
    private Object output; // 실제 데이터

    // 성공 여부 확인
    public boolean isSuccess() {
      return "0".equals(rtCd);
    }
  }

  // 성공 여부 확인
  public boolean isSuccess() {
    if (body instanceof KisResponseBody) {
      return ((KisResponseBody) body).isSuccess();
    }
    return body != null;
  }

  // 에러 메시지 반환
  public String getErrorMessage() {
    if (body instanceof KisResponseBody) {
      KisResponseBody responseBody = (KisResponseBody) body;
      return responseBody.getMsg1();
    }
    return "Unknown error";
  }
}