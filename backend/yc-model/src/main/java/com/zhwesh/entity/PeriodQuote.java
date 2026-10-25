package com.zhwesh.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 周/月/季/年K行情（对应 week_quote/month_quote/quarter_quote/year_quote 表）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeriodQuote {
    private Long id;              // 主键
    private String code;          // 股票代码
    private String periodKey;     // 周期标识（如 2026-W43、2026-10、2026-Q4、2026）
    private LocalDate date;       // 周期内最后一个交易日
    private BigDecimal open;      // 开盘价
    private BigDecimal high;      // 最高价
    private BigDecimal low;       // 最低价
    private BigDecimal close;     // 收盘价
    private BigDecimal preClose;  // 前一周/月/季/年收盘价
    private Long volume;          // 成交量（股）
    private BigDecimal amount;    // 成交金额
}
