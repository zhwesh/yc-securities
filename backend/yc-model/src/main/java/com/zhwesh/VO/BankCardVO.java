package com.zhwesh.VO;

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
public class BankCardVO {
    private Long id;                // 主键
    private Long accountId;         // 资金账号id
    private String cardNumber;      // 银行卡号
    private String bankName;        // 所属银行名称
    private String phone;           // 银行预留手机号（掩码）
    private LocalDateTime bindTime; // 绑卡时间
}
