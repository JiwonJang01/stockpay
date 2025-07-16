package com.project.stockpay.common.repository;

import com.project.stockpay.common.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, String> {
    // 계좌별 포트폴리오 조회
    List<Portfolio> findByAccount_AccountNum(String accountNum);
    // 종목별 포트폴리오 조회
    List<Portfolio> findByStock_StockTicker(String stockTicker);
    // 거래상태별 조회
    List<Portfolio> findByPortfolioTrStatus(String portfolioTrStatus);
}