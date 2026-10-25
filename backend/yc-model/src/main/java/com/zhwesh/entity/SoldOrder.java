package com.zhwesh.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 已成交委托单信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SoldOrder {
    private Long id;                    // id
    private Long orderId;               // 委托单id
    private Long accountId;             // 资金账户id
    private String stockCode;           // 股票代码
    private BigDecimal price;           // 委托价格
    private Integer quantity;           // 交易数量，正数买入，负数卖出
    private Integer takerSide;          // 主动方，0主动买，1主动卖
    private LocalDateTime createTime;   // 成交时间
}
