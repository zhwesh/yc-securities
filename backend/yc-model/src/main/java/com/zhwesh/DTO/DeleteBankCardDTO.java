package com.zhwesh.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 删除银行卡所需信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeleteBankCardDTO {
    private Long accountId;         // 资金账号id
    private String cardNumber;      // 银行卡号
    private String phone;           // 银行预留手机号
    private String code;            // 短信验证码
}
