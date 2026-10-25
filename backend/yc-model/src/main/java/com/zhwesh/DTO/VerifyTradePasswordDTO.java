package com.zhwesh.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 验证交易密码所需信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyTradePasswordDTO {
    private Long accountId;         // 资金账号id
    private String tradePassword;   // 交易密码
    private Integer onlineHours;    // 保持在线时长（小时），可选：6、12、24
}
