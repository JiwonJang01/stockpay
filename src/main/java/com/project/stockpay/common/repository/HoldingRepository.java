package com.project.stockpay.common.repository;

import com.project.stockpay.common.entity.Account;
import com.project.stockpay.common.entity.Holding;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface HoldingRepository extends JpaRepository<Holding, String> {

  // 계좌별 보유종목 조회
  List<Holding> findByAccount_AccountNum(String accountNum);

  // 종목별 보유현황 조회
  List<Holding> findByStock_StockTicker(String stockTicker);

  // 계좌와 보유 주식 코드 조회
  Optional<Holding> findByAccountAndStock_StockTicker(Account account, String stockTicker);
}