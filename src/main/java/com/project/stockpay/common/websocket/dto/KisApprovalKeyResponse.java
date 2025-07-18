package com.project.stockpay.common.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KisApprovalKeyResponse {

  @JsonProperty("approval_key")
  private String approvalKey;

  @JsonProperty("error_description")
  private String errorDescription;

  @JsonProperty("error_code")
  private String errorCode;

  // 성공 여부 확인
  public boolean isSuccess() {
    return approvalKey != null && !approvalKey.isEmpty();
  }

  // 에러 메시지 반환
  public String getErrorMessage() {
    if (errorDescription != null) {
      return errorDescription;
    }
    if (errorCode != null) {
      return "Error Code: " + errorCode;
    }
    return "Unknown error";
  }
}