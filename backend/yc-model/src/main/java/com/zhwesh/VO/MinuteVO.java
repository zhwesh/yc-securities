package com.zhwesh.VO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 分时图信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MinuteVO {
    private String code;                // 股票代码
    private BigDecimal preClose;        // 昨收价
    private List<MinuteData> minutes;   // 分时数据（按时间升序）

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MinuteData {
        private Long time;              // 时间戳（秒级，本地时间按UTC换算）
        private BigDecimal price;       // 当前价
        private BigDecimal avgPrice;    // 分时均价
        private Long volume;            // 该分钟成交量（手）
    }
}
