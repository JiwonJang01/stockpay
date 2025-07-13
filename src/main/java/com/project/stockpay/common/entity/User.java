package com.project.stockpay.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.sql.*;

@Entity
@Getter
@Setter
// @Table(name = "user")
public class User {
    // 고객 아이디 user_id
    @Id
    private String userId;
    // 비밀번호 user_pw
    private String userPw;
    // 이름 user_name
    private String userName;
    // 전화번호
    private Integer userPhone;
    // 생년월일
    private Date userBirth;
    // 데이터 생성 일시
    private Timestamp dataCreateTime;
    // 데이터 변경 일시
    private Timestamp dataChangeTime;
}
