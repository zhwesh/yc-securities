package com.zhwesh.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 银行卡信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankCard {
    private Long id;                    // id
    private String cardNumber;          // 银行卡号
    private String bankName;            // 银行名称
    private Long accountId;             // 资金账户id
    private String phone;               // 银行预留手机号
    private LocalDateTime createTime;   // 绑定时间
}
