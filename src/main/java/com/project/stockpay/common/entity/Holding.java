package com.project.stockpay.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.sql.*;

@Entity
@Getter
@Setter
// @Table(name = "holding")
public class Holding {
    // 보유번호
    @Id
    private String holdId;
    // 보유개수
    private Integer holdNum;
    // 매수가격
    private Integer holdBuyprice;
    // 데이터생성일시
    private Timestamp holdCreatetime;
    // 데이터변경일시
    private Timestamp holdChangetime;
    // 계좌번호 (FK)
    @ManyToOne
    @JoinColumn(name = "account_num")
    private Account account;
    // 종목티커 (FK)
    @ManyToOne
    @JoinColumn(name = "stock_ticker")
    private Stock stock;
}
