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
    private String name;
    // 상품가격
    private Integer price;
    // 상품종류
    private String category;
    // 상품설명
    private String description;
}
