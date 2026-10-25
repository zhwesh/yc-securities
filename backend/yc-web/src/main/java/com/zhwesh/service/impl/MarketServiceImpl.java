package com.zhwesh.service.impl;

import com.zhwesh.VO.*;
import com.zhwesh.cache.KLineCache;
import com.zhwesh.constant.KLineAdjust;
import com.zhwesh.constant.KLinePeriod;
import com.zhwesh.engine.market.MarketEngine;
import com.zhwesh.engine.match.MatchEngine;
import com.zhwesh.entity.DailyQuote;
import com.zhwesh.entity.MinuteQuote;
import com.zhwesh.entity.PeriodQuote;
import com.zhwesh.entity.SoldOrder;
import com.zhwesh.exception.BusinessException;
import com.zhwesh.mapper.OrderMapper;
import com.zhwesh.mapper.PeriodQuoteMapper;
import com.zhwesh.mapper.QuoteMapper;
import com.zhwesh.result.PageResult;
import com.zhwesh.service.MarketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Service
public class MarketServiceImpl implements MarketService {

    private static final int AVG_VOLUME_DAYS = 5;

    private static final int DEFAULT_TRADE_LIMIT = 50;

    private static final int MAX_TRADE_LIMIT = 200;

    private static final DateTimeFormatter TRADE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final int DEFAULT_KLINE_LIMIT = 200;

    private static final int MAX_KLINE_LIMIT = 1000;

    private static final int DEFAULT_MINUTE_KLINE_LIMIT = 240;

    private static final int MAX_MINUTE_KLINE_LIMIT = 500;

    private static final Set<Integer> MINUTE_KLINE_PERIODS = Set.of(1, 5, 15, 30, 60);

    private static final DateTimeFormatter MINUTE_KLINE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final MarketEngine marketEngine;
    private final MatchEngine matchEngine;
    private final QuoteMapper quoteMapper;
    private final PeriodQuoteMapper periodQuoteMapper;
    private final OrderMapper orderMapper;
    private final KLineCache kLineCache;

    @Autowired
    public MarketServiceImpl(MarketEngine marketEngine, MatchEngine matchEngine,
        QuoteMapper quoteMapper, PeriodQuoteMapper periodQuoteMapper,
        OrderMapper orderMapper, KLineCache kLineCache) {
        this.marketEngine = marketEngine;
        this.matchEngine = matchEngine;
        this.quoteMapper = quoteMapper;
        this.periodQuoteMapper = periodQuoteMapper;
        this.orderMapper = orderMapper;
        this.kLineCache = kLineCache;
    }

    /**
     * 查询个股最新行情快照
     * @param code 股票代码
     * @return 行情快照
     */
    @Override
    public StockQuoteVO getQuote(String code) {
        StockQuoteVO quote = marketEngine.getQuote(code);
        if (quote == null) {
            throw new BusinessException("股票不存在");
        }
        return quote;
    }

    /**
     * 批量查询个股最新行情
     * @param codes 股票代码
     * @return 行情快照列表
     */
    @Override
    public List<StockQuoteVO> getQuotes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        List<String> limitedCodes = codes.stream().limit(50).toList();
        List<StockQuoteVO> stockQuoteVOs = new ArrayList<>(limitedCodes.size());
        for (String code : limitedCodes) {
            StockQuoteVO quote = marketEngine.getQuote(code);
            if (quote != null) {
                stockQuoteVOs.add(quote);
            }
        }
        return stockQuoteVOs;
    }

    /**
     * 查询股票盘口
     * @param code 股票代码
     * @param depth 档位
     * @return 盘口
     */
    @Override
    public OrderBookVO getOrderBook(String code, Integer depth) {
        if (marketEngine.getQuote(code) == null) {
            throw new BusinessException("股票不存在");
        }
        int limit = Math.max(1, Math.min(depth == null ? 5 : depth, 20));
        return matchEngine.getSnapshot(code, limit);
    }

    /**
     * 分页查询股票排行榜
     * @param sortBy 排序指标
     * @param order 排序方向
     * @param pageNum 页号
     * @param pageSize 每页大小
     * @return 分页查询结果
     */
    @Override
    public PageResult getRankPage(Integer sortBy, Integer order, Integer pageNum, Integer pageSize) {
        try {
            return marketEngine.getRankPage(sortBy, order, pageNum, pageSize);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(e.getMessage());
        }
    }

    /**
     * 查询股票某日分时数据
     * @param code 股票代码
     * @param date 日期
     * @return 分时数据
     */
    @Override
    public MinuteVO getMinuteDatas(String code, LocalDate date) {
        StockQuoteVO quote = marketEngine.getQuote(code);
        if (quote == null) {
            throw new BusinessException("股票不存在");
        }
        LocalDate day = date == null ? LocalDate.now() : date;
        MinuteVO minuteVO = new MinuteVO();
        minuteVO.setCode(code);
        List<MinuteVO.MinuteData> minutes = new ArrayList<>();
        if (day.equals(LocalDate.now())) {
            minuteVO.setPreClose(quote.getPreClose());
            for (MinuteQuote minute : marketEngine.getTodayBars(code)) {
                minutes.add(toMinuteData(minute));
            }
        } else {
            DailyQuote daily = quoteMapper.getDailyByCodeAndDate(code, day);
            minuteVO.setPreClose(daily == null ? null : daily.getPreClose());
            for (MinuteQuote minute : quoteMapper.getMinutesByCodeAndDate(code, day)) {
                minutes.add(toMinuteData(minute));
            }
        }
        minuteVO.setMinutes(minutes);
        return minuteVO;
    }

    /**
     * 查询股票逐笔成交
     * @param code 股票代码
     * @param limit 成交笔数上限
     * @return 逐笔成交列表
     */
    @Override
    public List<StockTradeDetailVO> getTrades(String code, Integer limit) {
        if (marketEngine.getQuote(code) == null) {
            throw new BusinessException("股票不存在");
        }
        int size = limit == null ? DEFAULT_TRADE_LIMIT
            : Math.max(1, Math.min(limit, MAX_TRADE_LIMIT));
        List<SoldOrder> trades = orderMapper.getMarketTrades(code, size);
        List<StockTradeDetailVO> result = new ArrayList<>(trades.size());
        for (SoldOrder trade : trades) {
            result.add(StockTradeDetailVO.builder()
                .time(trade.getCreateTime().format(TRADE_TIME_FORMATTER))
                .price(trade.getPrice())
                .volume(trade.getQuantity().longValue())
                .direction(trade.getTakerSide() == null ? 0 : trade.getTakerSide())
                .build());
        }
        return result;
    }

    /**
     * 查询全部指数最新行情
     * @return 指数行情列表
     */
    @Override
    public List<IndexVO> getIndexes() {
        return marketEngine.getIndexes();
    }

    /**
     * 查询K线数据
     * @param code 股票代码
     * @param period 周期
     * @param limit 返回条数
     * @param endDate 分页游标
     * @param adjust 复权方式
     * @return K线数据
     */
    @Override
    public KLineVO getKLine(String code, Integer period, Integer limit, LocalDate endDate,
        Integer adjust) {
        if (marketEngine.getQuote(code) == null) {
            throw new BusinessException("\u80a1\u7968\u4e0d\u5b58\u5728");
        }
        Integer p = period == null ? KLinePeriod.DAY : period;
        if (!KLinePeriod.validPeriod.contains(p)) {
            throw new BusinessException("\u4e0d\u652f\u6301\u7684K\u7ebf\u5468\u671f\uff1a" + p);
        }
        Integer ad = adjust == null ? KLineAdjust.QFQ : adjust;
        if (!KLineAdjust.validAdjust.contains(ad)) {
            throw new BusinessException("\u4e0d\u652f\u6301\u7684\u590d\u6743\u65b9\u5f0f\uff1a" + ad);
        }
        int size = limit == null ? DEFAULT_KLINE_LIMIT
            : Math.max(1, Math.min(limit, MAX_KLINE_LIMIT));

        KLineCache.Snapshot snapshot = kLineCache.get(code);
        List<DailyQuote> dailies = snapshot.dailies();
        double[] factors = snapshot.factors();
        if (dailies.isEmpty()) {
            return KLineVO.builder().code(code).period(p).hasMore(false)
                .kLine(List.of()).build();
        }
        double lastFactor = factors[factors.length - 1];
        double base = KLineAdjust.QFQ == ad && lastFactor != 0 ? lastFactor : 1.0;

        List<KLineVO.KLineData> bars;
        boolean hasMore;
        if (KLinePeriod.DAY == p) {
            int endExclusive = endDate == null ? dailies.size() : lowerBound(dailies, endDate);
            hasMore = endExclusive > size;
            int from = Math.max(endExclusive - size, 0);
            bars = new ArrayList<>(endExclusive - from);
            for (int i = from; i < endExclusive; i++) {
                bars.add(toKLineData(dailies.get(i), ratio(factors[i], base, ad)));
            }
        } else {
            LocalDate today = marketEngine.getTradingDay();
            LocalDate currentStart = periodStart(p, today);
            LocalDate tableEnd = endDate == null || endDate.isAfter(currentStart)
                ? currentStart : endDate;
            List<PeriodQuote> rows = periodQuoteMapper.getPeriodQuotes(
                periodTable(p), code, tableEnd, size + 1);
            List<KLineVO.KLineData> combined = new ArrayList<>(rows.size() + 1);
            for (int i = rows.size() - 1; i >= 0; i--) {
                combined.add(toPeriodKLine(rows.get(i), snapshot, base, ad));
            }

            KLineVO.KLineData currentBar = buildCurrentPeriodBar(
                code, p, today, dailies, snapshot, base, ad);
            if (currentBar != null) {
                LocalDate currentDate = LocalDate.parse(currentBar.getTime());
                if (endDate == null || endDate.isAfter(currentDate)) {
                    combined.add(currentBar);
                }
            }
            hasMore = combined.size() > size;
            int from = Math.max(combined.size() - size, 0);
            bars = new ArrayList<>(combined.subList(from, combined.size()));
        }
        return KLineVO.builder()
            .code(code)
            .period(p)
            .hasMore(hasMore)
            .kLine(bars)
            .build();
    }

    /**
     * 查询分钟K线数据
     * @param code 股票代码
     * @param period 周期
     * @param limit 返回条数
     * @param endTime 分页游标
     * @return 分钟K线
     */
    @Override
    public MinuteKLineVO getMinuteKLine(String code, Integer period, Integer limit,
        LocalDateTime endTime) {
        if (marketEngine.getQuote(code) == null) {
            throw new BusinessException("股票不存在");
        }
        int p = period == null ? 5 : period;
        if (!MINUTE_KLINE_PERIODS.contains(p)) {
            throw new BusinessException("不支持的分钟K线周期：" + p);
        }
        int size = limit == null ? DEFAULT_MINUTE_KLINE_LIMIT
            : Math.max(1, Math.min(limit, MAX_MINUTE_KLINE_LIMIT));

        int candidateLimit = (size + 1) * p + 240;
        Map<LocalDateTime, MinuteQuote> merged = new HashMap<>();
        for (MinuteQuote quote : quoteMapper.getMinutesBefore(code, endTime, candidateLimit)) {
            merged.put(quote.getTradeTime(), quote);
        }
        for (MinuteQuote quote : marketEngine.getTodayBars(code)) {
            if (endTime == null || quote.getTradeTime().isBefore(endTime)) {
                merged.put(quote.getTradeTime(), quote);
            }
        }

        List<MinuteQuote> minutes = new ArrayList<>(merged.values());
        minutes.sort(Comparator.comparing(MinuteQuote::getTradeTime));
        TreeMap<LocalDateTime, MinuteBucket> buckets = new TreeMap<>();
        for (MinuteQuote minute : minutes) {
            LocalDateTime bucketTime = minuteBucketTime(minute.getTradeTime(), p);
            if (bucketTime == null) {
                continue;
            }
            buckets.computeIfAbsent(bucketTime, key -> new MinuteBucket()).add(minute);
        }

        List<MinuteKLineVO.MinuteKLineData> bars = new ArrayList<>(buckets.size());
        for (Map.Entry<LocalDateTime, MinuteBucket> entry : buckets.entrySet()) {
            bars.add(entry.getValue().toData(entry.getKey()));
        }
        boolean hasMore = bars.size() > size;
        if (hasMore) {
            bars = new ArrayList<>(bars.subList(bars.size() - size, bars.size()));
        }
        return MinuteKLineVO.builder()
            .code(code)
            .period(p)
            .hasMore(hasMore)
            .kLine(bars)
            .build();
    }

    /**
     * 计算分钟K线周期起点
     * @param time 交易时间
     * @param period 周期
     * @return 周期起点
     */
    private LocalDateTime minuteBucketTime(LocalDateTime time, int period) {
        int minuteOfDay = time.getHour() * 60 + time.getMinute();
        int index;
        if (minuteOfDay >= 570 && minuteOfDay <= 690) {
            index = Math.min(minuteOfDay - 570, 119);
        } else if (minuteOfDay >= 780 && minuteOfDay <= 900) {
            index = Math.min(120 + minuteOfDay - 780, 239);
        } else {
            return null;
        }
        int bucketIndex = index / period * period;
        LocalDate day = time.toLocalDate();
        return bucketIndex < 120
            ? day.atTime(9, 30).plusMinutes(bucketIndex)
            : day.atTime(13, 0).plusMinutes(bucketIndex - 120);
    }

    /**
     * 分钟K线聚合桶
     */
    private static class MinuteBucket {
        private BigDecimal open;
        private BigDecimal high;
        private BigDecimal low;
        private BigDecimal close;
        private long volume;
        private BigDecimal amount = BigDecimal.ZERO;

        private void add(MinuteQuote minute) {
            BigDecimal price = minute.getPrice();
            if (price == null) {
                return;
            }
            price = price.setScale(2, RoundingMode.HALF_UP);
            if (open == null) {
                open = price;
            }
            if (high == null || price.compareTo(high) > 0) {
                high = price;
            }
            if (low == null || price.compareTo(low) < 0) {
                low = price;
            }
            close = price;
            volume += minute.getVolume() == null ? 0L : minute.getVolume();
            amount = amount.add(minute.getAmount() == null ? BigDecimal.ZERO : minute.getAmount());
        }

        private MinuteKLineVO.MinuteKLineData toData(LocalDateTime time) {
            return MinuteKLineVO.MinuteKLineData.builder()
                .time(time.format(MINUTE_KLINE_TIME_FORMATTER))
                .open(open)
                .high(high)
                .low(low)
                .close(close)
                .volume(BigDecimal.valueOf(volume).movePointLeft(2))
                .turnover(amount)
                .build();
        }
    }

    /**
     * 加载股票日均成交量
     * @param beforeDate 截止日期
     * @return 股票代码与日均成交量映射
     */
    @Override
    public Map<String, Long> loadAvgDailyVolumes(LocalDate beforeDate) {
        List<DailyQuote> recent = quoteMapper.getRecentDailyVolumes(beforeDate, AVG_VOLUME_DAYS);
        Map<String, long[]> totals = new HashMap<>();
        for (DailyQuote quote : recent) {
            if (quote.getVolume() == null) {
                continue;
            }
            long[] acc = totals.computeIfAbsent(quote.getCode(), key -> new long[2]);
            acc[0] += quote.getVolume();
            acc[1]++;
        }
        Map<String, Long> result = new HashMap<>(totals.size());
        for (Map.Entry<String, long[]> entry : totals.entrySet()) {
            result.put(entry.getKey(), entry.getValue()[0] / entry.getValue()[1]);
        }
        return result;
    }

    /**
     * 转换分钟行情
     * @param quote 分钟行情
     * @return 分时数据
     */
    private MinuteVO.MinuteData toMinuteData(MinuteQuote quote) {
        return MinuteVO.MinuteData.builder()
            .time(quote.getTradeTime().toEpochSecond(ZoneOffset.UTC))
            .price(quote.getPrice())
            .avgPrice(quote.getAvgPrice())
            .volume(quote.getVolume() == null ? null : quote.getVolume() / 100L)
            .build();
    }


    /**
     * 计算复权日线
     * @param dailies 日线列表
     * @param adjust 复权方式
     * @return 复权日线列表
     */
    /**
     * 二分查找第一个日期不小于 endDate 的索引
     */
    private int lowerBound(List<DailyQuote> dailies, LocalDate endDate) {
        int low = 0;
        int high = dailies.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (dailies.get(mid).getDate().isBefore(endDate)) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    /**
     * 复权比例
     */
    private double ratio(double factor, double base, Integer adjust) {
        if (KLineAdjust.QFQ == adjust) {
            return base == 0 ? 1.0 : factor / base;
        }
        if (KLineAdjust.HFQ == adjust) {
            return factor;
        }
        return 1.0;
    }

    /**
     * 获取复权价格
     * @param price 原始价格
     * @param ratio 复权比例
     * @return 复权价格
     */
    private BigDecimal adjustPrice(BigDecimal price, double ratio) {
        if (price == null || ratio == 1.0) {
            return price;
        }
        return price.multiply(BigDecimal.valueOf(ratio)).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 转换日线数据
     * @param quote 日线
     * @param ratio 复权比例
     * @return K线数据
     */
    private KLineVO.KLineData toKLineData(DailyQuote quote, double ratio) {
        return KLineVO.KLineData.builder()
            .time(quote.getDate().toString())
            .open(adjustPrice(quote.getOpen(), ratio))
            .high(adjustPrice(quote.getHigh(), ratio))
            .low(adjustPrice(quote.getLow(), ratio))
            .close(adjustPrice(quote.getClose(), ratio))
            .volume(quote.getVolume() == null ? null
                : BigDecimal.valueOf(quote.getVolume()).movePointLeft(2))
            .turnover(quote.getAmount())
            .build();
    }

    /**
     * 转换周期K线数据
     * @param quote 周期行情
     * @param snapshot 缓存快照
     * @param base 基准复权比例
     * @param adjust 复权方式
     * @return K线数据
     */
    private KLineVO.KLineData toPeriodKLine(PeriodQuote quote, KLineCache.Snapshot snapshot,
        double base, Integer adjust) {
        double ratio = ratioAt(quote.getDate(), snapshot, base, adjust);
        return KLineVO.KLineData.builder()
            .time(quote.getDate().toString())
            .open(adjustPrice(quote.getOpen(), ratio))
            .high(adjustPrice(quote.getHigh(), ratio))
            .low(adjustPrice(quote.getLow(), ratio))
            .close(adjustPrice(quote.getClose(), ratio))
            .volume(quote.getVolume() == null ? null
                : BigDecimal.valueOf(quote.getVolume()).movePointLeft(2))
            .turnover(quote.getAmount())
            .build();
    }

    /**
     * 构建当前周期K线
     * @param code 股票代码
     * @param period 周期
     * @param today 当前日期
     * @param dailies 日线列表
     * @param snapshot 缓存快照
     * @param base 基准复权比例
     * @param adjust 复权方式
     * @return K线数据
     */
    private KLineVO.KLineData buildCurrentPeriodBar(String code, Integer period, LocalDate today,
        List<DailyQuote> dailies, KLineCache.Snapshot snapshot, double base, Integer adjust) {
        LocalDate start = periodStart(period, today);
        DailyQuote engineBar = marketEngine.getDailyBar(code);
        List<DailyQuote> range = new ArrayList<>();
        int index = lowerBound(dailies, start);
        for (; index < dailies.size(); index++) {
            DailyQuote quote = dailies.get(index);
            if (quote.getDate().isAfter(today)) {
                break;
            }
            if (engineBar != null && quote.getDate().equals(engineBar.getDate())) {
                continue;
            }
            range.add(quote);
        }
        if (engineBar != null && !engineBar.getDate().isBefore(start)
            && !engineBar.getDate().isAfter(today)) {
            range.add(engineBar);
        }
        if (range.isEmpty()) {
            return null;
        }

        BigDecimal open = range.get(0).getOpen();
        BigDecimal high = null;
        BigDecimal low = null;
        BigDecimal close = null;
        long volume = 0L;
        BigDecimal amount = BigDecimal.ZERO;
        LocalDate lastDate = start;
        for (DailyQuote quote : range) {
            if (quote.getHigh() != null && (high == null || quote.getHigh().compareTo(high) > 0)) {
                high = quote.getHigh();
            }
            if (quote.getLow() != null && (low == null || quote.getLow().compareTo(low) < 0)) {
                low = quote.getLow();
            }
            if (quote.getClose() != null) {
                close = quote.getClose();
            }
            lastDate = quote.getDate();
            volume += quote.getVolume() == null ? 0L : quote.getVolume();
            amount = amount.add(quote.getAmount() == null ? BigDecimal.ZERO : quote.getAmount());
        }
        double ratio = ratioAt(lastDate, snapshot, base, adjust);
        return KLineVO.KLineData.builder()
            .time(lastDate.toString())
            .open(adjustPrice(open, ratio))
            .high(adjustPrice(high, ratio))
            .low(adjustPrice(low, ratio))
            .close(adjustPrice(close, ratio))
            .volume(BigDecimal.valueOf(volume).movePointLeft(2))
            .turnover(amount)
            .build();
    }

    /**
     * 计算周期起始日期
     * @param period 周期
     * @param date 日期
     * @return 起始日期
     */
    private LocalDate periodStart(Integer period, LocalDate date) {
        if (KLinePeriod.WEEK == period) {
            return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }
        if (KLinePeriod.MONTH == period) {
            return date.withDayOfMonth(1);
        }
        if (KLinePeriod.QUARTER == period) {
            int month = (date.getMonthValue() - 1) / 3 * 3 + 1;
            return LocalDate.of(date.getYear(), month, 1);
        }
        if (KLinePeriod.YEAR == period) {
            return LocalDate.of(date.getYear(), 1, 1);
        }
        throw new BusinessException("不支持的K线周期：" + period);
    }

    /**
     * 获取周期聚合表名
     * @param period 周期
     * @return 表名
     */
    private String periodTable(Integer period) {
        if (KLinePeriod.WEEK == period) {
            return "week_quote";
        }
        if (KLinePeriod.MONTH == period) {
            return "month_quote";
        }
        if (KLinePeriod.QUARTER == period) {
            return "quarter_quote";
        }
        if (KLinePeriod.YEAR == period) {
            return "year_quote";
        }
        throw new BusinessException("不支持的K线周期：" + period);
    }

    /**
     * 获取复权比例
     * @param date 日期
     * @param snapshot 缓存快照
     * @param base 基准复权比例
     * @param adjust 复权方式
     * @return 复权比例
     */
    private double ratioAt(LocalDate date, KLineCache.Snapshot snapshot, double base,
        Integer adjust) {
        if (KLineAdjust.QFQ != adjust && KLineAdjust.HFQ != adjust) {
            return 1.0;
        }
        List<DailyQuote> dailies = snapshot.dailies();
        double[] factors = snapshot.factors();
        if (dailies.isEmpty() || factors.length == 0) {
            return 1.0;
        }
        int index = lowerBound(dailies, date);
        if (index >= dailies.size()) {
            index = dailies.size() - 1;
        } else if (dailies.get(index).getDate().isAfter(date) && index > 0) {
            index--;
        }
        double factor = factors[index];
        return KLineAdjust.QFQ == adjust ? (base == 0 ? 1.0 : factor / base) : factor;
    }
}
