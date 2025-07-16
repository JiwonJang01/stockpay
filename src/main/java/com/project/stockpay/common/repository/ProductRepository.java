package com.project.stockpay.common.repository;

import com.project.stockpay.common.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, String> {
    // 상품종류별 조회
    List<Product> findByCategory(String Category);
}