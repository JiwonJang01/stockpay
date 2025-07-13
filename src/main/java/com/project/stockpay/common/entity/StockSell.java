package com.project.stockpay.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.sql.*;

@Entity
@Getter
@Setter
// @Table(name = "stocksell")
public class StockSell {
    // 매도주문ID
    @Id
    private String stocksellId;
    // 매도가격
    private Integer stocksellPrice;
    // 매도수량
    private Integer stocksellNum;
    // 데이터생성일시
    private Timestamp stocksellCreatetime;
    // 데이터변경일시
    private Timestamp stocksellChangetime;
    // 매도상황
    private String stocksellStatus;
    // 계좌번호 (FK)
    @ManyToOne
    @JoinColumn(name = "account_num")
    private Account account;
    // 보유번호 (FK)
    @ManyToOne
    @JoinColumn(name = "hold_id")
    private Holding holding;
}
