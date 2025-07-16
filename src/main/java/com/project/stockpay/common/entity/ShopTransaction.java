package com.project.stockpay.common.entity;

import jakarta.persistence.*;
import lombok.*;
import java.sql.*;

@Entity
@Getter
@Setter
// @Table(name = "shop_transaction")
public class ShopTransaction {
    // 결제거래id
    @Id
    private String transactionId;
    // 거래금액
    private Integer transactionAmount;
    // 거래상태
    private String transactionStatus;
    // 처리시간
    private Timestamp transactionTime;
    // 거래유형
    private String transactionDesc;
    // 결제번호 (FK)
    @ManyToOne
    @JoinColumn(name = "payment_id")
    private Payment payment;
}
