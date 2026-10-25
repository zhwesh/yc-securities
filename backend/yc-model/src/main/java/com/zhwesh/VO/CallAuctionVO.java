package com.zhwesh.VO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 集合竞价快照
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallAuctionVO {
    private String code;                                   // 股票代码
    private Integer phase;                                 // 时段代码：0休市 1开盘竞价可撤 2开盘竞价不可撤 3开盘冻结 4连续交易 5收盘竞价
    private String phaseName;                              // 时段名称
    private BigDecimal indicativePrice;                    // 虚拟成交价（无撮合时为 null）
    private BigDecimal matchedVolume;                      // 虚拟可成交量（手）
    private BigDecimal unmatchedBuyVolume;                 // 未匹配买量（手）
    private BigDecimal unmatchedSellVolume;                // 未匹配卖量（手）
    private List<OrderBookVO.OrderBookItemVO> bids;        // 竞价买盘（手）
    private List<OrderBookVO.OrderBookItemVO> asks;        // 竞价卖盘（手）
    private LocalDateTime updateTime;                      // 快照时间
}
