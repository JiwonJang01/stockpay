package com.project.stockpay.common.repository;

import com.project.stockpay.common.entity.Bag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface BagRepository extends JpaRepository<Bag, String> {
    // 사용자별 장바구니 조회
    List<Bag> findByUser_UserId(String userId);
    // 상품별 장바구니 조회
    List<Bag> findByProduct_ProductId(String productId);
}
