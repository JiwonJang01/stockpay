package com.project.stockpay.common.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
// @Table(name = "bag")
public class Bag {
    // 장바구니 id
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bagId;
    // 상품이름
    private String productName;
    // 대량수량
    private Integer productCount;
    // 상품가격
    private Integer productPrice;
    // 상품아이디 (FK)
    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;
    // 고객아이디 (FK)
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
}
