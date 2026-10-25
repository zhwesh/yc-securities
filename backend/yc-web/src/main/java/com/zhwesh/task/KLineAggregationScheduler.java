package com.zhwesh.task;

import com.zhwesh.service.KLineAggregationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 收盘后K线刷盘：把日线聚合写入周/月/季/年K表
 * 每个交易日15:05执行一次，当前周期的数据会覆盖式更新
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "trading.kline-aggregation-enabled",
    havingValue = "true", matchIfMissing = true)
public class KLineAggregationScheduler {

    private final KLineAggregationService kLineAggregationService;

    @Autowired
    public KLineAggregationScheduler(KLineAggregationService kLineAggregationService) {
        this.kLineAggregationService = kLineAggregationService;
    }

    /**
     * 收盘后聚合（周一至周五15:05）
     */
    @Scheduled(cron = "0 5 15 * * MON-FRI")
    public void aggregateAfterClose() {
        try {
            kLineAggregationService.aggregateLatestPeriods();
            kLineAggregationService.aggregatePreviousPeriods();
        } catch (Exception e) {
            log.error("收盘后K线聚合失败", e);
        }
    }
}
