package com.zhwesh.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 开通资金账户所需信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenAccountDTO {
    private String password;    // 交易密码
}
