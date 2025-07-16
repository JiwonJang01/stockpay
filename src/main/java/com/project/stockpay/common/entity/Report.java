package com.project.stockpay.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.sql.*;

@Entity
@Getter
@Setter
// @Table(name = "report")
public class Report {
    // report_id
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reportId;
    // 전일종가
    private String preCloseprice;
    // 시가
    private Integer openPrice;
    // 고가
    private Integer highPrice;
    // 저가
    private Integer lowPrice;
    // 거래량
    private Integer volume;
    // 데이터생성일시
    private Timestamp createTimestamp;
    // 데이터변경일시
    private Timestamp chageTimestamp;
    // 종목티커 (FK)
    @ManyToOne
    @JoinColumn(name = "stock_ticker")
    private Stock stock;
    // 계좌번호 (FK)
    @ManyToOne
    @JoinColumn(name = "account_num")
    private Account account;
}
