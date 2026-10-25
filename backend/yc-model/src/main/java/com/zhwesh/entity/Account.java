package com.zhwesh.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 资金账户信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {
    private Long id;                    // id
    private String password;            // 交易密码
    private Long userId;                // 所属用户id
    private BigDecimal availableAssets; // 可用资产
    private BigDecimal frozenAssets;    // 冻结资产（买入委托冻结）
    private String boards;              // 可交易板块，逗号分隔
    private LocalDateTime createTime;   // 创建时间
    private Integer status;             // 账户状态：1正常 2账户冻结
}
