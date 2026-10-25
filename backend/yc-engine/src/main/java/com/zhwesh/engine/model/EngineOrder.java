package com.zhwesh.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 引擎内存委托单（与orders表对应，id由数据库生成后回填）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EngineOrder {
    private Long id;                 // 委托单id（orders 表生成）
    private Long accountId;          // 资金账户id
    private String code;             // 股票代码
    private OrderSide side;          // 买卖方向
    private EngineOrderType type;    // 委托类型
    private BigDecimal price;        // 委托价格（市价单为 null）
    private BigDecimal freezePrice;  // 买入冻结价（用于集合竞价/连续交易结算）
    private long quantity;           // 委托数量（绝对值）
    private long filledQuantity;     // 已成交数量
    private LocalDateTime orderTime; // 下单时间（同价时间优先语义依据，实际由队列顺序保证）

    /**
     * 剩余未成交数量
     */
    public long getRemainingQuantity() {
        return quantity - filledQuantity;
    }

    /**
     * 是否已全部成交
     */
    public boolean isFilled() {
        return filledQuantity >= quantity;
    }
}
