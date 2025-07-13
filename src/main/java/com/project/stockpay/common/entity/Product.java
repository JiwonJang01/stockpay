package com.project.stockpay.common.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
// @Table(name = "product")
public class Product {
    // 상품아이디
    @Id
    private String productId;
    // 상품이름
    private String productName;
    // 상품가격
    private Integer productPrice;
    // 상품종류
    private String productCategory;
    // 상품설명
    private String productDec;
}
