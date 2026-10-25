package com.zhwesh.VO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 个股实时行情快照
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockQuoteVO {
    private String code;                      // 股票代码
    private String name;                      // 股票名称
    private BigDecimal price;                 // 现价
    private BigDecimal change;                // 涨跌额
    private BigDecimal changePercent;         // 涨跌幅（百分比）
    private BigDecimal open;                  // 今开
    private BigDecimal high;                  // 最高
    private BigDecimal low;                   // 最低
    private BigDecimal preClose;              // 昨收
    private BigDecimal volume;                // 成交量（手，1 手 = 100 股）
    private BigDecimal amount;                // 成交额
    private BigDecimal turnoverRate;          // 换手率（百分比）
    private BigDecimal amplitude;             // 振幅（百分比）
    private BigDecimal volumeRatio;           // 量比
    private BigDecimal pe;                    // 市盈率
    private BigDecimal marketValue;           // 总市值
    private BigDecimal circulateMarketValue;  // 流通市值
    private BigDecimal pb;                    // 市净率
}
