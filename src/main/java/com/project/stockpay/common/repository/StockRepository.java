package com.project.stockpay.common.repository;

import com.project.stockpay.common.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockRepository extends JpaRepository<Stock, String> {
    // 종목상태별 조회
    List<Stock> findByStockStatus(String stockStatus);
    // 종목섹터별 조회
    List<Stock> findByStockSector(String stockSector);
}