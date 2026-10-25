package com.zhwesh.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 出金所需信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawDTO {
    private Long accountId;         // 资金账号id
    private BigDecimal amount;      // 出金金额
}
