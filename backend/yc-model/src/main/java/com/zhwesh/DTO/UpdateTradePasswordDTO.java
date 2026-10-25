package com.zhwesh.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 修改交易密码所需信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTradePasswordDTO {
    private Long accountId;         // 资金账号id
    private String oldPassword;     // 原交易密码
    private String newPassword;     // 新交易密码
}
