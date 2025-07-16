package com.project.stockpay.common.repository;

import com.project.stockpay.common.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {
    // 사용자별 결제내역 조회
    List<Payment> findByUser_UserId(String userId);
    // 주문별 결제내역 조회
    List<Payment> findByOrder_OrderId(String orderId);
    // 결제상태별 조회
    List<Payment> findByPaymentStatus(String paymentStatus);
}
