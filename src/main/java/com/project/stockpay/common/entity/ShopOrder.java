package com.project.stockpay.common.entity;

import jakarta.persistence.*;
import lombok.*;
import java.sql.*;

@Entity
@Getter
@Setter
// @Table(name = "shop_order")
public class ShopOrder {
    // 주문번호
    @Id
    private String orderId;
    // 받는사람이름
    private String reciver;
    // 받는주소
    private String reciverAdd;
    // 받는사람전화번호
    private Integer reciverPhone;
    // 요청사항
    private String reciverRequire;
    // 총가격
    private Integer totalPrice;
    // 주문날짜
    private Date orderDate;
    // 고객아이디 (FK)
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
}
