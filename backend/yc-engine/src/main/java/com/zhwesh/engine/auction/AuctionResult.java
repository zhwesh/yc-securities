package com.zhwesh.engine.auction;

import com.zhwesh.engine.model.EngineOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 集合竞价撮合结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuctionResult {
    private String code;
    private BigDecimal price;             // 集合竞价成交价，未成交为 null
    private long matchedVolume;           // 成交数量（股）
    private List<AuctionMatch> matches;   // 成交配对
    private List<EngineOrder> residualOrders; // 未成交委托（含部分成交余量）
}
