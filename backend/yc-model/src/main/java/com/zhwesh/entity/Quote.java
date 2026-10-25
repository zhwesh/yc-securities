package com.zhwesh.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * k线记录（日/周/月共用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Quote {
    private Long id;                // id
    private String code;            // 股票代码
    private LocalDate date;         // 日期
    private BigDecimal open;        // 开盘价
    private BigDecimal high;        // 最高价
    private BigDecimal low;         // 最低价
    private BigDecimal close;       // 收盘价
    private BigDecimal preClose;    // 上个交易日的收盘价
    private Long volume;            // 成交量（股）
    private BigDecimal amount;      // 成交额
}
