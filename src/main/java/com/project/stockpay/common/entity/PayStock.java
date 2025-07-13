package com.project.stockpay.common.entity;

import jakarta.persistence.*;
import lombok.*;
import java.sql.*;

@Entity
@Getter
@Setter
// @Table(name = "pay_stock")
public class PayStock {
    // 결제사용주식id
    @Id
    private String paystckId;
    // 사용주식수량
    private Integer useStock;
    // 결제시점주가
    private Integer stockPrice;
    // 현금환산액
    private Integer convStock;
    // 매도주문상태
    private String sellStatus;
    // 매도주문시간
    private Timestamp sellDate;
    // 종목티커 (FK)
    @ManyToOne
    @JoinColumn(name = "stock_ticker")
    private Stock stock;
    // 결제번호 (FK)
    @ManyToOne
    @JoinColumn(name = "payment_id")
    private Payment payment;
    // 주문번호 (FK)
    @ManyToOne
    @JoinColumn(name = "order_id")
    private ShopOrder shopOrder;
    // 반환금
    private Integer refundAmount;
    // 반환상태
    private String refundStatus;
    // 반환일로시
    private Timestamp refundTime;
}
