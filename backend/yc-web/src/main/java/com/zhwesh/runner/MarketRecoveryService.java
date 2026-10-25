package com.zhwesh.runner;

import com.zhwesh.engine.market.MarketEngine;
import com.zhwesh.entity.DailyQuote;
import com.zhwesh.entity.MinuteQuote;
import com.zhwesh.entity.Stock;
import com.zhwesh.mapper.QuoteMapper;
import com.zhwesh.mapper.StockMapper;
import com.zhwesh.service.MarketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 行情引擎启动初始化：从stock/daily_quote/minute_quote恢复昨收、当日行情与分时
 */
@Component
public class MarketRecoveryService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MarketRecoveryService.class);

    private final MarketEngine marketEngine;
    private final StockMapper stockMapper;
    private final QuoteMapper quoteMapper;
    private final MarketService marketService;

    @Autowired
    public MarketRecoveryService(MarketEngine marketEngine, StockMapper stockMapper,
        QuoteMapper quoteMapper, MarketService marketService) {
        this.marketEngine = marketEngine;
        this.stockMapper = stockMapper;
        this.quoteMapper = quoteMapper;
        this.marketService = marketService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            recover();
        } catch (Exception e) {
            log.error("行情引擎初始化失败", e);
        }
    }

    /**
     * 恢复行情引擎状态
     */
    private void recover() {
        List<Stock> stocks = stockMapper.getAllStocks();
        if (stocks.isEmpty()) {
            log.warn("stock 表为空，行情引擎未初始化");
            return;
        }
        LocalDate today = LocalDate.now();
        Map<String, DailyQuote> latestMap = quoteMapper.getLatestDailies().stream()
            .collect(Collectors.toMap(DailyQuote::getCode, quote -> quote));

        Map<String, BigDecimal> preCloses = new HashMap<>(stocks.size());
        for (Stock stock : stocks) {
            preCloses.put(stock.getCode(),
                resolvePreClose(stock, latestMap.get(stock.getCode()), today));
        }
        Map<String, Long> avgVolumes = marketService.loadAvgDailyVolumes(today);
        marketEngine.initStocks(stocks, preCloses, avgVolumes);

        List<DailyQuote> todayDailies = quoteMapper.getDailiesByDate(today);
        if (!todayDailies.isEmpty()) {
            marketEngine.restoreToday(todayDailies);
        }

        Map<String, List<MinuteQuote>> minuteMap = quoteMapper.getMinutesByDate(today).stream()
            .collect(Collectors.groupingBy(MinuteQuote::getCode));
        for (Map.Entry<String, List<MinuteQuote>> entry : minuteMap.entrySet()) {
            marketEngine.restoreTodayBars(entry.getKey(), entry.getValue());
        }

        log.info("行情引擎初始化完成：股票 {} 只，当日日线 {} 条，当日分钟 {} 只股票",
            stocks.size(), todayDailies.size(), minuteMap.size());
    }

    /**
     * 计算昨收：当日已有行情用其昨收；否则用最近一条日线收盘价；无日线用上市初始价
     */
    private BigDecimal resolvePreClose(Stock stock, DailyQuote latest, LocalDate today) {
        if (latest == null) {
            return stock.getInitialPrice() == null ? BigDecimal.ONE : stock.getInitialPrice();
        }
        if (today.equals(latest.getDate()) && latest.getPreClose() != null) {
            return latest.getPreClose();
        }
        return latest.getClose() == null ? BigDecimal.ONE : latest.getClose();
    }
}
