package com.zhwesh.service;

import java.time.LocalDate;

public interface KLineAggregationService {
    /**
     * 聚合最新交易日所在周期的周/月/季/年K
     */
    void aggregateLatestPeriods();

    /**
     * 聚合最新交易日的前一周/月/季/年K（启动补跑，防止收盘时服务未运行）
     */
    void aggregatePreviousPeriods();

    /**
     * 聚合指定交易日所在周期的周/月/季/年K
     * @param tradeDate 交易日
     */
    void aggregate(LocalDate tradeDate);
}
