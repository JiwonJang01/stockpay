package com.project.stockpay.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.sql.*;

@Entity
@Getter
@Setter
// @Table(name = "stockbuy")
public class StockBuy {
    // 매수주문ID
    @Id
    private String stockbuyId;
    // 매수가격
    private Integer stockbuyPrice;
    // 매수량
    private Integer stockbuyNum;
    // 데이터생성일시
    private Timestamp stockbuyCreatetime;
    // 데이터변경일시
    private Timestamp stockbuyChangetime;
    // 매수상황
    private String stockbuyStatus;
    // 계좌번호 (FK)
    @ManyToOne
    @JoinColumn(name = "account_num")
    private Account account;
    // 종목티커 (FK)
    @ManyToOne
    @JoinColumn(name = "stock_ticker")
    private Stock stock;
}
