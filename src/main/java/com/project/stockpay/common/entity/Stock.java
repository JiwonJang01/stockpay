package com.project.stockpay.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.sql.*;

@Entity
@Getter
@Setter
// @Table(name = "stock")
public class Stock {
    // 종목티커
    @Id
    private String stockTicker;
    // 거래소코드
    private String stockCode;
    // 종목상태
    private String stockStatus;
    // 종목명
    private String stockName;
    // 종목설명
    private String stockDec;
    // 종목섹터
    private String stockSector;
    // 상장일자
    private Date stockOpen;
    // 상장폐지일자
    private Date stockClose;
    // 데이터생성일자
    private Timestamp stockCreatetime;
    // 데이터변경일자
    private Timestamp stockChangetime;
}
