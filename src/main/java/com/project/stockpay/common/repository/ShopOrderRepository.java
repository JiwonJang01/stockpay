package com.project.stockpay.common.repository;

import com.project.stockpay.common.entity.ShopOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ShopOrderRepository extends JpaRepository<ShopOrder, String> {
    // 사용자별 주문 조회
    List<ShopOrder> findByUser_UserId(String userId);
}