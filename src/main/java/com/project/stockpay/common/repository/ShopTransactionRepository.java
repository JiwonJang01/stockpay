package com.project.stockpay.common.repository;

import com.project.stockpay.common.entity.ShopTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ShopTransactionRepository extends JpaRepository<ShopTransaction, String> {
    // 결제별 거래내역 조회
    List<ShopTransaction> findByPayment_PaymentId(String paymentId);
    // 거래상태별 조회
    List<ShopTransaction> findByTransactionStatus(String transactionStatus);
}