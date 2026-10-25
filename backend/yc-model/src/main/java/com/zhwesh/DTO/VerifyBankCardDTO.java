package com.zhwesh.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 验证银行卡号与预留手机号所需信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyBankCardDTO {
    private String cardNumber;  // 银行卡号
    private String phone;       // 银行预留手机号
}
