package com.project.stockpay.common.repository;

import com.project.stockpay.common.entity.StockSell;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface StockSellRepository extends JpaRepository<StockSell, String> {
    // 계좌별 매도주문 조회
    List<StockSell> findByAccount_AccountNum(String accountNum);
    // 매도상황별 조회
    List<StockSell> findByStocksellStatus(String stocksellStatus);
}