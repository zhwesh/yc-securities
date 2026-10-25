package com.zhwesh.engine.auction;

import com.zhwesh.engine.model.EngineOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 集合竞价成交配对
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuctionMatch {
    private EngineOrder buyer;
    private EngineOrder seller;
    private BigDecimal price;
    private long quantity;
}
