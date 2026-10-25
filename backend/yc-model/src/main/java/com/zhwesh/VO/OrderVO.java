package com.zhwesh.VO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 委托单信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderVO {
    private Long id;                 // 委托单id
    private Long accountId;          // 资金账号id
    private String stockCode;        // 股票代码
    private String stockName;        // 股票名称
    private BigDecimal price;        // 委托价格
    private Integer quantity;        // 委托数量，正数买入，负数卖出
    private Integer soldQuantity;    // 已成交数量，非负
    private Integer type;            // 委托类型：1限价 2市价
    private Integer status;          // 委托状态：1未成交 2部分成交 3全部成交 4已撤单
    private LocalDateTime createTime; // 委托时间
}
