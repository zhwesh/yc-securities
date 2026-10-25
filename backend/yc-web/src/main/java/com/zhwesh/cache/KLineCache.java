package com.zhwesh.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zhwesh.entity.DailyQuote;
import com.zhwesh.mapper.QuoteMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * K线数据缓存：按股票缓存原始日线与累计复权因子
 * 日线历史数据不变；当日快照落库后由 SnapshotScheduler 主动失效对应股票。
 */
@Component
public class KLineCache {

    private final QuoteMapper quoteMapper;
    private final Cache<String, Snapshot> cache;

    @Autowired
    public KLineCache(QuoteMapper quoteMapper,
        @Value("${trading.kline-cache.max-stocks:200}") long maxStocks,
        @Value("${trading.kline-cache.expire-after-access-seconds:3600}") long expireSeconds) {
        this.quoteMapper = quoteMapper;
        this.cache = Caffeine.newBuilder()
            .maximumSize(maxStocks)
            .expireAfterAccess(Duration.ofSeconds(expireSeconds))
            .build();
    }

    /**
     * 获取股票K线快照（未命中时从数据库加载，同一股票并发请求只查一次库）
     * @param code 股票代码
     * @return 日线与复权因子
     */
    public Snapshot get(String code) {
        return cache.get(code, this::load);
    }

    /**
     * 失效单只股票缓存（当日快照落库后调用）
     * @param code 股票代码
     */
    public void invalidate(String code) {
        cache.invalidate(code);
    }

    private Snapshot load(String code) {
        List<DailyQuote> dailies = quoteMapper.getDailiesByCode(code);
        return new Snapshot(dailies, buildFactors(dailies));
    }

    /**
     * 累计后复权因子：除权日（pre_close 与前一日收盘不同）乘以 prevClose/preClose
     */
    private double[] buildFactors(List<DailyQuote> dailies) {
        double[] factors = new double[dailies.size()];
        double factor = 1.0;
        BigDecimal prevClose = null;
        for (int i = 0; i < dailies.size(); i++) {
            DailyQuote daily = dailies.get(i);
            if (prevClose != null && daily.getPreClose() != null
                && prevClose.signum() != 0 && daily.getPreClose().signum() != 0
                && daily.getPreClose().compareTo(prevClose) != 0) {
                factor *= prevClose.doubleValue() / daily.getPreClose().doubleValue();
            }
            factors[i] = factor;
            prevClose = daily.getClose();
        }
        return factors;
    }

    /**
     * 单只股票K线快照（只读）
     * @param dailies 日线（按日期升序）
     * @param factors 累计后复权因子
     */
    public record Snapshot(List<DailyQuote> dailies, double[] factors) {
    }
}
