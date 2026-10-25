package com.zhwesh.engine.auction;

import com.zhwesh.VO.OrderBookVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 集合竞价快照
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuctionSnapshot {
    private String code;
    private BigDecimal indicativePrice;  // 虚拟成交价
    private long matchedVolume;          // 虚拟可成交量（股）
    private long unmatchedBuyVolume;     // 未匹配买量（股）
    private long unmatchedSellVolume;    // 未匹配卖量（股）
    private List<OrderBookVO.OrderBookItemVO> bids;
    private List<OrderBookVO.OrderBookItemVO> asks;
}
