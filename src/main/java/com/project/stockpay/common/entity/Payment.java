package com.project.stockpay.common.entity;

import jakarta.persistence.*;
import lombok.*;
import java.sql.*;

@Entity
@Getter
@Setter
// @Table(name = "payment")
public class Payment {
    // 결제번호
    @Id
    private String paymentId;
    // 결제상태
    private String paymentStatus;
    // 총결제금액
    private Integer totalPay;
    // 결제요청시간
    private Timestamp payDate;
    // 결제완료시간
    private Timestamp payendTime;
    // 고객아이디 (FK)
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
    // 주문번호 (FK)
    @ManyToOne
    @JoinColumn(name = "order_id")
    private ShopOrder order;
    // 초과금
    private Integer excessRefund;
}
