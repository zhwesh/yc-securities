package com.zhwesh.VO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * k线图信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KLineVO {
    private String code;                // 股票代码
    private Integer period;             // 周期：1日K 2周K 3月K
    private Boolean hasMore;            // 是否存在前驱数据
    private List<KLineData> kLine;      // K线数组

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KLineData {
        private String time;            // 时间（yyyy-MM-dd）
        private BigDecimal open;        // 开盘价
        private BigDecimal high;        // 最高价
        private BigDecimal low;         // 最低价
        private BigDecimal close;       // 收盘价
        private BigDecimal volume;      // 成交量（手，1 手 = 100 股）
        private BigDecimal turnover;    // 成交额（可选）
    }
}
