package com.zhwesh.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 用户持仓信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Position {
    private Long id;                  // id
    private Long accountId;           // 资金账户id
    private String code;              // 股票代码
    private String stockName;         // 股票名称
    private BigDecimal costPrice;     // 持仓成本价
    private Long quantity;            // 总持仓数量
    private Long availableQuantity;   // 可卖数量（T+1）
    private Long todayBuyQuantity;    // 当日买入数量（T+1，当日不可卖）
}
