package com.zhwesh.task;

import com.zhwesh.cache.KLineCache;
import com.zhwesh.engine.market.MarketEngine;
import com.zhwesh.engine.match.MatchEngine;
import com.zhwesh.entity.DailyQuote;
import com.zhwesh.entity.MinuteQuote;
import com.zhwesh.mapper.PositionMapper;
import com.zhwesh.mapper.QuoteMapper;
import com.zhwesh.service.MarketService;
import com.zhwesh.service.OrderService;
import com.zhwesh.util.TradingTimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 行情快照调度器
 */
@Component
@ConditionalOnProperty(name = "trading.snapshot-enabled", havingValue = "true", matchIfMissing = true)
public class SnapshotScheduler {

    private static final Logger log = LoggerFactory.getLogger(SnapshotScheduler.class);

    private final MarketEngine marketEngine;
    private final QuoteMapper quoteMapper;
    private final TradingTimeUtil tradingTimeUtil;
    private final MarketService marketService;
    private final MatchEngine matchEngine;
    private final OrderService orderService;
    private final PositionMapper positionMapper;
    private final KLineCache kLineCache;

    /**
     * 上次落库日期
     */
    private volatile LocalDate lastFlushDay;

    @Autowired
    public SnapshotScheduler(MarketEngine marketEngine, QuoteMapper quoteMapper,
        TradingTimeUtil tradingTimeUtil, MarketService marketService, MatchEngine matchEngine,
        OrderService orderService, PositionMapper positionMapper, KLineCache kLineCache) {
        this.marketEngine = marketEngine;
        this.quoteMapper = quoteMapper;
        this.tradingTimeUtil = tradingTimeUtil;
        this.marketService = marketService;
        this.matchEngine = matchEngine;
        this.orderService = orderService;
        this.positionMapper = positionMapper;
        this.kLineCache = kLineCache;
    }

    /**
     * 初始化交易日
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeTradingDay() {
        LocalDate today = LocalDate.now();
        orderService.cancelPendingOrdersBefore(today);
        positionMapper.resetDailyAvailable(today);
        lastFlushDay = today;
    }

    /**
     * 执行分钟行情落库
     */
    @Scheduled(cron = "0 * * * * ?")
    public void flush() {
        if (marketEngine.getStockCount() == 0) {
            return;
        }
        LocalDate today = LocalDate.now();
        try {
            if (lastFlushDay == null) {
                lastFlushDay = today;
                return;
            }
            if (!today.equals(lastFlushDay)) {
                flushSnapshots();
                orderService.cancelAllPendingOrders();
                positionMapper.resetDailyAvailable(today);
                marketEngine.resetDay(today);
                marketEngine.updateAvgDailyVolumes(marketService.loadAvgDailyVolumes(today));
                matchEngine.clearTrades();
                lastFlushDay = today;
                return;
            }
            if (!tradingTimeUtil.isTradingTime()) {
                return;
            }
            flushSnapshots();
        } catch (Exception e) {
            log.error("行情落库失败", e);
        }
    }

    /**
     * 封存分钟行情与当日日线
     */
    private void flushSnapshots() {
        List<MinuteQuote> minutes = marketEngine.tickMinute();
        if (!minutes.isEmpty()) {
            quoteMapper.batchUpsertMinutes(minutes);
        }
        List<DailyQuote> dailies = marketEngine.getDailyBars();
        if (!dailies.isEmpty()) {
            quoteMapper.batchUpsertDailies(dailies);
            // 失效K线缓存
            for (DailyQuote daily : dailies) {
                kLineCache.invalidate(daily.getCode());
            }
        }
    }
}
