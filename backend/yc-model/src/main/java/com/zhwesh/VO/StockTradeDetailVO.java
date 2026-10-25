package com.zhwesh.VO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 逐笔成交明细
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockTradeDetailVO {
    private String time;         // 成交时间（HH:mm:ss）
    private BigDecimal price;    // 成交价格
    private Long volume;         // 成交量（股）
    private Integer direction;   // 买卖方向：0买 1卖
}
