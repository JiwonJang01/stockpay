package com.project.stockpay.common.repository;

import com.project.stockpay.common.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CartRepository extends JpaRepository<Cart, String> {
    // 사용자별 장바구니 조회
    List<Cart> findByUser_UserId(String userId);
    // 상품별 장바구니 조회
    List<Cart> findByProduct_ProductId(String productId);
}
