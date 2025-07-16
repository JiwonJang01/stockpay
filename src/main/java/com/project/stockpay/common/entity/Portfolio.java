package com.project.stockpay.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.sql.*;

@Entity
@Getter
@Setter
// @Table(name = "portfolio")
public class Portfolio {
    // 포트폴리오ID
    @Id
    private String portfolioId;
    // 종목티커 (FK)
    @ManyToOne
    @JoinColumn(name = "stock_ticker")
    private Stock stock;
    // 계좌번호 (FK)
    @ManyToOne
    @JoinColumn(name = "account_num")
    private Account account;
    // 거래타입
    private String portfolioTrType;
    // 거래상태
    private String portfolioTrStatus;
    // 주문ID
    private String portfolioOrderId;
    // 주문가
    private Integer portfolioOrderPrice;
    // 실제 체결가
    private Integer portfolioRealPrice;
    // 거래수량
    private Integer quantity;
    // 거래 전 보유 수량
    private Integer quantityBefore;
    // 거래 후 보유 수량
    private Integer quantityAfter;
    // 주문 시간
    private Timestamp orderTime;
    // 체결 시간
    private Timestamp executedTime;
}
