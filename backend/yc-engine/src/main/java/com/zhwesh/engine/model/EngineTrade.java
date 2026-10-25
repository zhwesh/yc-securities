package com.zhwesh.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 引擎撮合成交记录（一笔成交对应买卖双方两个订单）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EngineTrade {
    private Long takerOrderId;       // 主动方订单id
    private Long makerOrderId;       // 被动方订单id
    private Long takerAccountId;     // 主动方资金账户id
    private Long makerAccountId;     // 被动方资金账户id
    private String code;             // 股票代码
    private OrderSide takerSide;     // 主动方方向（BUY=主动买，SELL=主动卖）
    private BigDecimal price;        // 成交价格（被动方挂单价）
    private long quantity;           // 成交数量
    private LocalDateTime tradeTime; // 成交时间
}
