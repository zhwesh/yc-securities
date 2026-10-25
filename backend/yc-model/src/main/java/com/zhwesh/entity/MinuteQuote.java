package com.zhwesh.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 分钟行情（对应 minute_quote 表）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MinuteQuote {
    private Long id;              // 主键
    private String code;          // 股票代码
    private LocalDateTime tradeTime; // 分钟起点
    private BigDecimal price;     // 该分钟最后成交价
    private Long volume;          // 分钟成交量（股）
    private BigDecimal amount;    // 分钟成交额（元）
    private BigDecimal avgPrice;  // 当日均价
}
