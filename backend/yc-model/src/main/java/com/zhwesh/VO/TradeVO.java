package com.zhwesh.VO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 成交记录信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeVO {
    private Long id;                 // 成交记录id
    private Long orderId;            // 委托单id
    private Long accountId;          // 资金账号id
    private String stockCode;        // 股票代码
    private String stockName;        // 股票名称
    private BigDecimal price;        // 成交价格
    private Integer quantity;        // 成交数量，正数买入，负数卖出
    private BigDecimal amount;       // 成交金额，卖出为负数
    private LocalDateTime createTime; // 成交时间
}
