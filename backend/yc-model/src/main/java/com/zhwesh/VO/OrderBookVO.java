package com.zhwesh.VO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 股票盘口
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderBookVO {
    private String code;                 // 股票代码
    private Integer depth;               // 盘口档位数
    private List<OrderBookItemVO> asks;  // 卖盘（按价格从低到高，最多 depth 档）
    private List<OrderBookItemVO> bids;  // 买盘（按价格从高到低，最多 depth 档）

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderBookItemVO {
        private BigDecimal price;   // 档位价格
        private BigDecimal volume;  // 档位数量（手，1 手 = 100 股）
    }
}
