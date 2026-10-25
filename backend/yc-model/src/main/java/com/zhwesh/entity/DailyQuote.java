package com.zhwesh.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 日行情（对应 daily_quote 表）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyQuote {
    private Long id;              // 主键
    private String code;          // 股票代码
    private LocalDate date;       // 交易日
    private BigDecimal open;      // 开盘价
    private BigDecimal high;      // 最高价
    private BigDecimal low;       // 最低价
    private BigDecimal close;     // 收盘价
    private BigDecimal preClose;  // 昨收
    private Long volume;          // 成交量（股）
    private BigDecimal amount;    // 成交额（元）
}
