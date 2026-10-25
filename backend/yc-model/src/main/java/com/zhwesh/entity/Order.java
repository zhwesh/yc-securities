package com.zhwesh.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 委托单信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    private Long id;                    // id
    private String orderKey;            // UUID幂等键
    private Long accountId;             // 资金账户id
    private String stockCode;           // 股票代码
    private BigDecimal price;           // 委托价格
    private BigDecimal freezePrice;     // 冻结价（买入：限价=委托价，市价=涨停价；卖出为空）
    private Integer quantity;           // 交易数量（正数买入，负数卖出）
    private Integer soldQuantity;       // 已成交数量
    private Integer type;               // 订单种类：1限价单 2市价单
    private Integer status;             // 交易状态：1未成交 2部分成交 3全部成交 4撤单
    private LocalDateTime createTime;   // 下单时间
}
