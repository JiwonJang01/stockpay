package com.project.stockpay.common.repository;

import com.project.stockpay.common.entity.Account;
import com.project.stockpay.common.entity.StockBuy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface StockBuyRepository extends JpaRepository<StockBuy, String> {

  // 계좌별 매수주문 조회
  List<StockBuy> findByAccount_AccountNum(String accountNum);

  // 매수상황별 조회
  List<StockBuy> findByStockbuyStatus(String stockbuyStatus);

  // 계좌와 매수 상황 조회
  List<StockBuy> findByAccountAndStockbuyStatus(Account account, String pending);
}