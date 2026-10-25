package com.zhwesh.VO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 分钟级K线数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MinuteKLineVO {
    private String code;                  // 股票代码
    private Integer period;               // 周期（分钟）：1/5/15/30/60
    private Boolean hasMore;              // 是否还有更早数据
    private List<MinuteKLineData> kLine;  // 分钟K线列表

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MinuteKLineData {
        private String time;          // 周期开始时间（yyyy-MM-dd HH:mm）
        private BigDecimal open;     // 开盘价
        private BigDecimal high;      // 最高价
        private BigDecimal low;       // 最低价
        private BigDecimal close;     // 收盘价
        private BigDecimal volume;    // 成交量（手）
        private BigDecimal turnover;  // 成交额（元）
    }
}
