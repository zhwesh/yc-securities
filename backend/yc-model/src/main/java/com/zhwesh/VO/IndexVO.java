package com.zhwesh.VO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 指数行情
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexVO {
    private String code;              // 指数代码
    private String name;              // 指数名称
    private BigDecimal price;         // 当前点位
    private BigDecimal change;        // 涨跌点数
    private BigDecimal changePercent; // 涨跌幅（百分比）
    private BigDecimal volume;        // 成交量（手，1 手 = 100 股）
    private BigDecimal amount;        // 成交额
}
