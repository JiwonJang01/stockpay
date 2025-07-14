package com.project.stockpay.common.repository;

import com.project.stockpay.common.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AccountRepository extends JpaRepository<Account, String> {

  // 사용자별 계좌 조회
  List<Account> findByUserId(String userId);
}