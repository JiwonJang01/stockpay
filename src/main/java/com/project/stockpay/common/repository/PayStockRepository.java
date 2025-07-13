package com.project.stockpay.common.repository;

import com.project.stockpay.common.entity.PayStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PayStockRepository extends JpaRepository<PayStock, String> {
    // 결제별 사용주식 조회
    List<PayStock> findByPayment_PaymentId(String paymentId);
    // 주문별 사용주식 조회
    List<PayStock> findByShopOrder_OrderId(String orderId);
    // 종목별 사용내역 조회
    List<PayStock> findByStock_StockTicker(String stockTicker);
}