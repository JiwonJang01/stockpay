package com.project.stockpay.common.repository;

import com.project.stockpay.common.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, String> {
    // 종목별 현황 조회
    List<Report> findByStock_StockTicker(String stockTicker);
    // 계좌별 현황 조회
    List<Report> findByAccount_AccountNum(String accountNum);
}
