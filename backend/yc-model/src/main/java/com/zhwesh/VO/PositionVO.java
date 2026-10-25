package com.zhwesh.VO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 持仓信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionVO {
    private Long id;                     // 持仓记录id
    private Long accountId;              // 资金账号id
    private String code;                 // 股票代码
    private String stockName;            // 股票名称
    private BigDecimal costPrice;        // 成本价
    private Long quantity;              // 持仓数量
    private Long availableQuantity;     // 可卖数量
    private BigDecimal currentPrice;     // 现价
    private BigDecimal marketValue;      // 持仓市值
    private BigDecimal profitLoss;       // 浮动盈亏
    private BigDecimal profitLossRatio;  // 盈亏比例
}
