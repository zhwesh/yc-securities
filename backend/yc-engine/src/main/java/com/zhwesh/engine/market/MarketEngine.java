package com.zhwesh.engine.market;

import com.zhwesh.VO.IndexVO;
import com.zhwesh.VO.StockQuoteVO;
import com.zhwesh.constant.RankOrder;
import com.zhwesh.constant.RankSort;
import com.zhwesh.entity.DailyQuote;
import com.zhwesh.entity.MinuteQuote;
import com.zhwesh.entity.Stock;
import com.zhwesh.result.PageResult;
import com.zhwesh.util.PageUtil;
import com.zhwesh.util.TradingTimeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 行情引擎：维护全市场实时行情状态（最新价、开高低、成交量额、分钟聚合、日聚合、指数）
 * 行情只由真实成交驱动（客户撮合成交、做市机器人成交），不做随机波动。
 * 对外直接输出 yc-model 的 VO/实体，避免重复定义模型。
 */
@Component
public class MarketEngine {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private static final int TRADING_MINUTES = 240;

    /**
     * 指数定义（代码前缀区分成分股）
     */
    private static final List<IndexDef> INDEXES = List.of(
        new IndexDef("SH000001", "上证指数", "60", BigDecimal.valueOf(3000)),
        new IndexDef("SZ399001", "深证成指", "00", BigDecimal.valueOf(10000)),
        new IndexDef("SZ399006", "创业板指", "30", BigDecimal.valueOf(2000)),
        new IndexDef("SH000688", "科创50", "68", BigDecimal.valueOf(900)),
        new IndexDef("BJ899050", "北证50", "92", BigDecimal.valueOf(1000))
    );

    private final TradingTimeUtil tradingTimeUtil;

    /**
     * 全部股票行情状态
     */
    private final Map<String, StockState> stocks = new ConcurrentHashMap<>();

    /**
     * 当前交易日
     */
    private volatile LocalDate tradingDay = LocalDate.now();

    @Autowired
    public MarketEngine(TradingTimeUtil tradingTimeUtil) {
        this.tradingTimeUtil = tradingTimeUtil;
    }

    /**
     * 初始化股票列表（应用启动时调用）
     * @param stockList 股票静态信息
     * @param preCloses 股票代码 -> 昨收
     * @param avgDailyVolumes 股票代码 -> 近5日日均成交量（股）
     */
    public synchronized void initStocks(List<Stock> stockList, Map<String, BigDecimal> preCloses,
        Map<String, Long> avgDailyVolumes) {
        stocks.clear();
        for (Stock stock : stockList) {
            StockState state = new StockState();
            state.code = stock.getCode();
            state.name = stock.getName();
            state.board = stock.getBoard();
            state.shares = stock.getCirculateShares() == null ? 0L : stock.getCirculateShares();
            state.totalShares = stock.getTotalShares() == null ? 0L : stock.getTotalShares();
            state.bookValue = stock.getBookValue();
            state.eps = stock.getEps();
            state.preClose = preCloses.getOrDefault(stock.getCode(), BigDecimal.ONE);
            state.avgDailyVolume = avgDailyVolumes.getOrDefault(stock.getCode(), 0L);
            state.lastPrice = state.preClose;
            stocks.put(state.code, state);
        }
    }

    /**
     * 恢复当日日线状态（重启时从 daily_quote 恢复：今开/最高/最低/最新价/量额）
     * @param dailies 当日日线数据
     */
    public synchronized void restoreToday(List<DailyQuote> dailies) {
        for (DailyQuote daily : dailies) {
            StockState state = stocks.get(daily.getCode());
            if (state == null || daily.getOpen() == null) {
                continue;
            }
            state.open = daily.getOpen();
            state.high = daily.getHigh();
            state.low = daily.getLow();
            state.lastPrice = daily.getClose();
            state.volume = daily.getVolume() == null ? 0L : daily.getVolume();
            state.amount = daily.getAmount() == null ? BigDecimal.ZERO : daily.getAmount();
            state.hasTrade = true;
            if (daily.getPreClose() != null) {
                state.preClose = daily.getPreClose();
            }
        }
    }

    /**
     * 恢复当日已封存的分时数据（重启时从 minute_quote 恢复）
     * @param code 股票代码
     * @param minutes 当日分钟数据（按时间升序）
     */
    public synchronized void restoreTodayBars(String code, List<MinuteQuote> minutes) {
        StockState state = stocks.get(code);
        if (state == null || minutes == null || minutes.isEmpty()) {
            return;
        }
        state.todayBars.addAll(minutes);
        state.hasTrade = true;
        MinuteQuote last = minutes.get(minutes.size() - 1);
        if (state.lastPrice == null && last.getPrice() != null) {
            state.lastPrice = last.getPrice();
        }
        LocalDateTime nowMinute = LocalDateTime.now().withSecond(0).withNano(0);
        startMinute(state, state.lastPrice == null ? BigDecimal.ONE : state.lastPrice, nowMinute);
    }

    /**
     * 成交通知：更新最新价、高低、量额与当前分钟聚合
     * @param code 股票代码
     * @param price 成交价
     * @param quantity 成交数量（股）
     */
    public synchronized void onTrade(String code, BigDecimal price, long quantity) {
        StockState state = stocks.get(code);
        if (state == null || price == null || quantity <= 0) {
            return;
        }
        price = price.setScale(2, RoundingMode.HALF_UP);
        BigDecimal turnover = price.multiply(BigDecimal.valueOf(quantity));
        LocalDateTime nowMinute = LocalDateTime.now().withSecond(0).withNano(0);

        if (state.minuteStart == null) {
            startMinute(state, price, nowMinute);
        } else if (state.minuteStart.isBefore(nowMinute)) {
            state.todayBars.add(buildMinuteQuote(state));
            startMinute(state, state.lastPrice == null ? price : state.lastPrice, nowMinute);
        }

        if (!state.hasTrade) {
            state.open = price;
            state.high = price;
            state.low = price;
            state.hasTrade = true;
        } else {
            if (state.high == null || price.compareTo(state.high) > 0) {
                state.high = price;
            }
            if (state.low == null || price.compareTo(state.low) < 0) {
                state.low = price;
            }
        }
        state.lastPrice = price;
        state.volume += quantity;
        state.amount = state.amount.add(turnover);

        if (state.minuteHigh == null || price.compareTo(state.minuteHigh) > 0) {
            state.minuteHigh = price;
        }
        if (state.minuteLow == null || price.compareTo(state.minuteLow) < 0) {
            state.minuteLow = price;
        }
        state.minuteLast = price;
        state.minuteVolume += quantity;
        state.minuteAmount = state.minuteAmount.add(turnover);
    }

    /**
     * 每整分钟调用：封存上一分钟的聚合，开出新分钟
     * @return 本次封存的分钟行情（供落库）
     */
    public synchronized List<MinuteQuote> tickMinute() {
        LocalDateTime nowMinute = LocalDateTime.now().withSecond(0).withNano(0);
        List<MinuteQuote> minutes = new ArrayList<>();
        for (StockState state : stocks.values()) {
            if (!state.hasTrade || state.minuteStart == null) {
                continue;
            }
            if (!state.minuteStart.isBefore(nowMinute)) {
                continue;
            }
            MinuteQuote minute = buildMinuteQuote(state);
            state.todayBars.add(minute);
            minutes.add(minute);
            startMinute(state, state.lastPrice, nowMinute);
        }
        return minutes;
    }

    /**
     * 查询单只股票实时行情
     */
    public synchronized StockQuoteVO getQuote(String code) {
        StockState state = stocks.get(code);
        return state == null ? null : toStockQuoteVO(state);
    }

    /**
     * 查询全部股票实时行情
     */
    public synchronized List<StockQuoteVO> getQuotes() {
        List<StockQuoteVO> quotes = new ArrayList<>(stocks.size());
        for (StockState state : stocks.values()) {
            quotes.add(toStockQuoteVO(state));
        }
        return quotes;
    }

    /**
     * 查询单只股票当日日线（今日无成交返回 null）
     */
    public synchronized DailyQuote getDailyBar(String code) {
        StockState state = stocks.get(code);
        if (state == null || !state.hasTrade || state.open == null) {
            return null;
        }
        return toDailyQuote(state);
    }

    /**
     * 查询全部当日有成交的日线（供落库）
     */
    public synchronized List<DailyQuote> getDailyBars() {
        List<DailyQuote> bars = new ArrayList<>();
        for (StockState state : stocks.values()) {
            if (state.hasTrade && state.open != null) {
                bars.add(toDailyQuote(state));
            }
        }
        return bars;
    }

    /**
     * 查询某只股票当日分时（已封存 + 当前分钟实时点）
     */
    public synchronized List<MinuteQuote> getTodayBars(String code) {
        StockState state = stocks.get(code);
        if (state == null) {
            return List.of();
        }
        List<MinuteQuote> minutes = new ArrayList<>(state.todayBars);
        if (state.hasTrade && state.minuteStart != null) {
            minutes.add(buildMinuteQuote(state));
        }
        return minutes;
    }

    /**
     * 分页查询股票排行榜（按行情指标排序）
     * @param sortBy 排序指标
     * @param order 排序方向：asc/desc
     * @param pageNum 页号
     * @param pageSize 每页大小
     * @return 分页结果（total + StockQuoteVO 列表）
     */
    public synchronized PageResult getRankPage(Integer sortBy, Integer order, Integer pageNum,
        Integer pageSize) {
        int field = sortBy == null ? RankSort.CHANGE_PERCENT : sortBy;
        if (!RankSort.validSort.contains(field)) {
            throw new IllegalArgumentException("不支持的排序指标：" + field);
        }
        if (order != null && !RankOrder.validOrder.contains(order)) {
            throw new IllegalArgumentException("不支持的排序方向：" + order);
        }
        boolean asc = order != null && order == RankOrder.ASC;
        Comparator<BigDecimal> decimalComparator = Comparator.nullsLast(asc
            ? Comparator.<BigDecimal>naturalOrder()
            : Comparator.<BigDecimal>reverseOrder());
        Comparator<Long> longComparator = Comparator.nullsLast(asc
            ? Comparator.<Long>naturalOrder()
            : Comparator.<Long>reverseOrder());
        Comparator<StockState> comparator = switch (field) {
            case RankSort.PRICE -> Comparator.comparing((StockState state) -> state.lastPrice,
                decimalComparator);
            case RankSort.CHANGE_PERCENT -> Comparator.comparing(this::changePercent,
                decimalComparator);
            case RankSort.VOLUME -> Comparator.comparing((StockState state) -> state.volume,
                longComparator);
            case RankSort.AMOUNT -> Comparator.comparing((StockState state) -> state.amount,
                decimalComparator);
            case RankSort.TURNOVER_RATE -> Comparator.comparing(this::turnoverRate,
                decimalComparator);
            case RankSort.PB -> Comparator.comparing(this::pb, decimalComparator);
            case RankSort.MARKET_VALUE -> Comparator.comparing(
                (StockState state) -> marketValue(state, state.totalShares), decimalComparator);
            case RankSort.CIRCULATE_MARKET_VALUE -> Comparator.comparing(
                (StockState state) -> marketValue(state, state.shares), decimalComparator);
            case RankSort.BOOK_VALUE -> Comparator.comparing((StockState state) -> state.bookValue,
                decimalComparator);
            default -> throw new IllegalStateException();
        };
        List<StockState> sorted = new ArrayList<>(stocks.values());
        sorted.sort(comparator);
        int offset = PageUtil.getOffset(pageNum, pageSize);
        int size = PageUtil.getPageSize(pageSize);
        int from = Math.min(offset, sorted.size());
        int to = Math.min(offset + size, sorted.size());
        List<StockQuoteVO> records = new ArrayList<>(to - from);
        for (int i = from; i < to; i++) {
            records.add(toStockQuoteVO(sorted.get(i)));
        }
        return new PageResult(sorted.size(), records);
    }

    /**
     * 查询全部指数实时行情（流通市值加权）
     */
    public synchronized List<IndexVO> getIndexes() {
        List<IndexVO> indexes = new ArrayList<>(INDEXES.size());
        for (IndexDef def : INDEXES) {
            long volume = 0L;
            BigDecimal amount = BigDecimal.ZERO;
            BigDecimal currentMarketValue = BigDecimal.ZERO;
            BigDecimal baseMarketValue = BigDecimal.ZERO;
            for (StockState state : stocks.values()) {
                if (!state.code.startsWith(def.prefix) || state.shares <= 0
                    || state.lastPrice == null || state.preClose == null) {
                    continue;
                }
                BigDecimal shares = BigDecimal.valueOf(state.shares);
                currentMarketValue = currentMarketValue.add(state.lastPrice.multiply(shares));
                baseMarketValue = baseMarketValue.add(state.preClose.multiply(shares));
                volume += state.volume;
                amount = amount.add(state.amount);
            }

            BigDecimal price = def.basePoint;
            BigDecimal change = BigDecimal.ZERO;
            BigDecimal changePercent = BigDecimal.ZERO;
            if (baseMarketValue.signum() > 0) {
                price = currentMarketValue.divide(baseMarketValue, 10, RoundingMode.HALF_UP)
                    .multiply(def.basePoint)
                    .setScale(2, RoundingMode.HALF_UP);
                change = price.subtract(def.basePoint);
                changePercent = change.multiply(HUNDRED)
                    .divide(def.basePoint, 2, RoundingMode.HALF_UP);
            }
            indexes.add(IndexVO.builder()
                .code(def.code)
                .name(def.name)
                .price(price)
                .change(change)
                .changePercent(changePercent)
                .volume(BigDecimal.valueOf(volume).movePointLeft(2))
                .amount(amount)
                .build());
        }
        return indexes;
    }

    /**
     * 跨日重置：昨收 = 昨日最后成交价（昨日无成交则沿用原昨收），当日行情清零
     * @param newDay 新交易日
     */
    public synchronized void resetDay(LocalDate newDay) {
        for (StockState state : stocks.values()) {
            if (state.hasTrade && state.lastPrice != null) {
                state.preClose = state.lastPrice;
            }
            state.open = null;
            state.high = null;
            state.low = null;
            state.volume = 0L;
            state.amount = BigDecimal.ZERO;
            state.hasTrade = false;
            state.lastPrice = state.preClose;
            state.minuteStart = null;
            state.minuteOpen = null;
            state.minuteHigh = null;
            state.minuteLow = null;
            state.minuteLast = null;
            state.minuteVolume = 0L;
            state.minuteAmount = BigDecimal.ZERO;
            state.todayBars.clear();
        }
        this.tradingDay = newDay;
    }

    /**
     * 当前交易日
     */
    public LocalDate getTradingDay() {
        return tradingDay;
    }

    /**
     * 更新近N日日均成交量（跨日时由 web 层重新加载后调用）
     */
    public synchronized void updateAvgDailyVolumes(Map<String, Long> avgVolumes) {
        for (Map.Entry<String, Long> entry : avgVolumes.entrySet()) {
            StockState state = stocks.get(entry.getKey());
            if (state != null && entry.getValue() != null) {
                state.avgDailyVolume = entry.getValue();
            }
        }
    }

    /**
     * 已初始化股票数量
     */
    public int getStockCount() {
        return stocks.size();
    }

    /**
     * 开始新的分钟聚合
     */
    private void startMinute(StockState state, BigDecimal price, LocalDateTime minuteStart) {
        state.minuteStart = minuteStart;
        state.minuteOpen = price;
        state.minuteHigh = price;
        state.minuteLow = price;
        state.minuteLast = price;
        state.minuteVolume = 0L;
        state.minuteAmount = BigDecimal.ZERO;
    }

    /**
     * 构建当前分钟（或已封存分钟）行情
     */
    private MinuteQuote buildMinuteQuote(StockState state) {
        BigDecimal avgPrice = BigDecimal.ZERO;
        if (state.volume > 0 && state.amount.signum() > 0) {
            avgPrice = state.amount.divide(BigDecimal.valueOf(state.volume), 2, RoundingMode.HALF_UP);
        }
        return MinuteQuote.builder()
            .code(state.code)
            .tradeTime(state.minuteStart)
            .price(state.minuteLast)
            .volume(state.minuteVolume)
            .amount(state.minuteAmount)
            .avgPrice(avgPrice)
            .build();
    }

    /**
     * 行情状态转实时行情快照
     */
    private StockQuoteVO toStockQuoteVO(StockState state) {
        return StockQuoteVO.builder()
            .code(state.code)
            .name(state.name)
            .price(state.lastPrice)
            .change(change(state))
            .changePercent(changePercent(state))
            .open(state.open)
            .high(state.high)
            .low(state.low)
            .preClose(state.preClose)
            .volume(BigDecimal.valueOf(state.volume).movePointLeft(2))
            .amount(state.amount)
            .turnoverRate(turnoverRate(state))
            .amplitude(amplitude(state))
            .volumeRatio(calcVolumeRatio(state))
            .pe(pe(state))
            .marketValue(marketValue(state, state.totalShares))
            .circulateMarketValue(marketValue(state, state.shares))
            .pb(pb(state))
            .build();
    }

    /**
     * 行情状态转当日日线
     */
    private DailyQuote toDailyQuote(StockState state) {
        return DailyQuote.builder()
            .code(state.code)
            .date(tradingDay)
            .open(state.open)
            .high(state.high)
            .low(state.low)
            .close(state.lastPrice)
            .preClose(state.preClose)
            .volume(state.volume)
            .amount(state.amount)
            .build();
    }

    /**
     * 涨跌额
     */
    private BigDecimal change(StockState state) {
        if (state.lastPrice == null || state.preClose == null || state.preClose.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return state.lastPrice.subtract(state.preClose).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 涨跌幅（%）
     */
    private BigDecimal changePercent(StockState state) {
        if (state.preClose == null || state.preClose.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return change(state).multiply(HUNDRED).divide(state.preClose, 2, RoundingMode.HALF_UP);
    }

    /**
     * 换手率（%）
     */
    private BigDecimal turnoverRate(StockState state) {
        if (state.shares <= 0) {
            return null;
        }
        return BigDecimal.valueOf(state.volume).multiply(HUNDRED)
            .divide(BigDecimal.valueOf(state.shares), 2, RoundingMode.HALF_UP);
    }

    /**
     * 振幅（%）
     */
    private BigDecimal amplitude(StockState state) {
        if (state.high == null || state.low == null || state.preClose == null
            || state.preClose.signum() <= 0) {
            return null;
        }
        return state.high.subtract(state.low).multiply(HUNDRED)
            .divide(state.preClose, 2, RoundingMode.HALF_UP);
    }

    /**
     * 量比 = 当日成交量 / (近N日日均每分钟成交量 × 今日已交易分钟数)
     */
    private BigDecimal calcVolumeRatio(StockState state) {
        if (state.volume <= 0 || state.avgDailyVolume <= 0) {
            return null;
        }
        int elapsedMinutes = Math.max(tradingTimeUtil.getElapsedTradingMinutes(), 1);
        BigDecimal avgMinuteVolume = BigDecimal.valueOf(state.avgDailyVolume)
            .divide(BigDecimal.valueOf(TRADING_MINUTES), 4, RoundingMode.HALF_UP);
        BigDecimal denominator = avgMinuteVolume.multiply(BigDecimal.valueOf(elapsedMinutes));
        if (denominator.signum() <= 0) {
            return null;
        }
        return BigDecimal.valueOf(state.volume).divide(denominator, 2, RoundingMode.HALF_UP);
    }

    /**
     * 市盈率 = 最新价 / 每股收益
     */
    private BigDecimal pe(StockState state) {
        if (state.eps == null || state.eps.signum() <= 0 || state.lastPrice == null) {
            return null;
        }
        return state.lastPrice.divide(state.eps, 2, RoundingMode.HALF_UP);
    }

    /**
     * 市净率 = 最新价 / 每股净资产
     */
    private BigDecimal pb(StockState state) {
        if (state.bookValue == null || state.bookValue.signum() <= 0 || state.lastPrice == null) {
            return null;
        }
        return state.lastPrice.divide(state.bookValue, 2, RoundingMode.HALF_UP);
    }

    /**
     * 市值 = 最新价 × 股本
     */
    private BigDecimal marketValue(StockState state, long shares) {
        if (shares <= 0 || state.lastPrice == null) {
            return null;
        }
        return state.lastPrice.multiply(BigDecimal.valueOf(shares));
    }

    /**
     * 单只股票内存行情状态
     */
    private static class StockState {
        private String code;
        private String name;
        private String board;
        private long shares;
        private long totalShares;
        private BigDecimal preClose;
        private BigDecimal bookValue;
        private BigDecimal eps;
        private long avgDailyVolume;
        private BigDecimal open;
        private BigDecimal high;
        private BigDecimal low;
        private BigDecimal lastPrice;
        private long volume;
        private BigDecimal amount = BigDecimal.ZERO;
        private boolean hasTrade;
        private LocalDateTime minuteStart;
        private BigDecimal minuteOpen;
        private BigDecimal minuteHigh;
        private BigDecimal minuteLow;
        private BigDecimal minuteLast;
        private long minuteVolume;
        private BigDecimal minuteAmount = BigDecimal.ZERO;
        private final List<MinuteQuote> todayBars = new ArrayList<>();
    }

    /**
     * 指数定义
     */
    private static class IndexDef {
        private final String code;
        private final String name;
        private final String prefix;
        private final BigDecimal basePoint;

        private IndexDef(String code, String name, String prefix, BigDecimal basePoint) {
            this.code = code;
            this.name = name;
            this.prefix = prefix;
            this.basePoint = basePoint;
        }
    }
}
