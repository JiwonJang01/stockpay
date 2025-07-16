package com.project.stockpay.common.repository;

import com.project.stockpay.common.entity.AccountHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AccountHistoryRepository extends JpaRepository<AccountHistory, String> {
    // 계좌별 거래내역 조회
    List<AccountHistory> findByAccount_AccountNum(String accountNum);
}
