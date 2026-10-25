package com.zhwesh.service.impl;

import com.zhwesh.constant.KLinePeriod;
import com.zhwesh.mapper.PeriodQuoteMapper;
import com.zhwesh.mapper.QuoteMapper;
import com.zhwesh.service.KLineAggregationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;

@Slf4j
@Service
public class KLineAggregationServiceImpl implements KLineAggregationService {

    /**
     * 日线表
     */
    private static final String DAILY_TABLE = "daily_quote";

    /**
     * 周K表
     */
    private static final String WEEK_TABLE = "week_quote";

    /**
     * 月K表
     */
    private static final String MONTH_TABLE = "month_quote";

    /**
     * 季K表
     */
    private static final String QUARTER_TABLE = "quarter_quote";

    /**
     * 年K表
     */
    private static final String YEAR_TABLE = "year_quote";

    private final PeriodQuoteMapper periodQuoteMapper;
    private final QuoteMapper quoteMapper;

    @Autowired
    public KLineAggregationServiceImpl(PeriodQuoteMapper periodQuoteMapper,
        QuoteMapper quoteMapper) {
        this.periodQuoteMapper = periodQuoteMapper;
        this.quoteMapper = quoteMapper;
    }

    /**
     * 聚合最新交易日所在周期的周/月/季/年K
     * 周/月来自日线，季来自月K，年来自季K，避免每天扫描全量日线
     */
    @Override
    public void aggregateLatestPeriods() {
        LocalDate latest = quoteMapper.getLatestDailyDate();
        if (latest == null) {
            log.warn("日线数据为空，跳过K线聚合");
            return;
        }
        aggregatePeriods(latest);
    }

    /**
     * 聚合最新交易日的前一周/月/季/年K（补跑用）
     */
    @Override
    public void aggregatePreviousPeriods() {
        LocalDate latest = quoteMapper.getLatestDailyDate();
        if (latest == null) {
            return;
        }
        aggregatePeriod(KLinePeriod.WEEK, weekStart(latest).minusDays(1), DAILY_TABLE);
        aggregatePeriod(KLinePeriod.MONTH, monthStart(latest).minusDays(1), DAILY_TABLE);
        aggregatePeriod(KLinePeriod.QUARTER, quarterStart(latest).minusDays(1), MONTH_TABLE);
        aggregatePeriod(KLinePeriod.YEAR, yearStart(latest).minusDays(1), QUARTER_TABLE);
    }

    /**
     * 聚合指定交易日所在周期的周/月/季/年K
     * @param tradeDate 交易日
     */
    @Override
    public void aggregate(LocalDate tradeDate) {
        if (tradeDate != null) {
            aggregatePeriods(tradeDate);
        }
    }

    /**
     * 聚合一个交易日所属的各个周期，并补齐季/年所需的月/季基础数据
     */
    private void aggregatePeriods(LocalDate tradeDate) {
        aggregatePeriod(KLinePeriod.WEEK, tradeDate, DAILY_TABLE);
        aggregatePeriod(KLinePeriod.MONTH, tradeDate, DAILY_TABLE);

        LocalDate quarterStart = quarterStart(tradeDate);
        ensureMonths(quarterStart, tradeDate);
        aggregatePeriod(KLinePeriod.QUARTER, tradeDate, MONTH_TABLE);

        ensurePastQuarters(tradeDate);
        aggregatePeriod(KLinePeriod.YEAR, tradeDate, QUARTER_TABLE);
    }

    /**
     * 补齐区间内缺失的月K（以日线为源）
     */
    private void ensureMonths(LocalDate startMonth, LocalDate endDate) {
        LocalDate cursor = monthStart(startMonth);
        LocalDate lastMonth = monthStart(endDate);
        while (!cursor.isAfter(lastMonth)) {
            String key = String.format("%d-%02d", cursor.getYear(), cursor.getMonthValue());
            if (periodQuoteMapper.countByPeriodKey(MONTH_TABLE, key) == 0) {
                aggregatePeriod(KLinePeriod.MONTH, cursor, DAILY_TABLE);
            }
            cursor = cursor.plusMonths(1);
        }
    }

    /**
     * 补齐本年度当前季度之前缺失的季K（以月K为源）
     */
    private void ensurePastQuarters(LocalDate latest) {
        LocalDate currentQuarterStart = quarterStart(latest);
        LocalDate cursor = quarterStart(yearStart(latest));
        while (cursor.isBefore(currentQuarterStart)) {
            String key = String.format("%d-Q%d", cursor.getYear(),
                (cursor.getMonthValue() - 1) / 3 + 1);
            if (periodQuoteMapper.countByPeriodKey(QUARTER_TABLE, key) == 0) {
                ensureMonths(cursor, cursor.plusMonths(3).minusDays(1));
                aggregatePeriod(KLinePeriod.QUARTER, cursor, MONTH_TABLE);
            }
            cursor = cursor.plusMonths(3);
        }
    }

    /**
     * 聚合单个周期
     */
    private void aggregatePeriod(int period, LocalDate date, String sourceTable) {
        LocalDate start;
        LocalDate end;
        String periodKey;
        String targetTable;
        if (KLinePeriod.WEEK == period) {
            start = weekStart(date);
            end = start.plusDays(6);
            periodKey = String.format("%d-W%02d",
                start.get(IsoFields.WEEK_BASED_YEAR),
                start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            targetTable = WEEK_TABLE;
        } else if (KLinePeriod.MONTH == period) {
            start = monthStart(date);
            end = start.with(TemporalAdjusters.lastDayOfMonth());
            periodKey = String.format("%d-%02d", start.getYear(), start.getMonthValue());
            targetTable = MONTH_TABLE;
        } else if (KLinePeriod.QUARTER == period) {
            start = quarterStart(date);
            end = start.plusMonths(3).minusDays(1);
            periodKey = String.format("%d-Q%d", start.getYear(),
                (start.getMonthValue() - 1) / 3 + 1);
            targetTable = QUARTER_TABLE;
        } else if (KLinePeriod.YEAR == period) {
            start = yearStart(date);
            end = start.plusYears(1).minusDays(1);
            periodKey = String.valueOf(start.getYear());
            targetTable = YEAR_TABLE;
        } else {
            throw new IllegalArgumentException("不支持的K线周期：" + period);
        }

        try {
            int rows = periodQuoteMapper.upsertPeriodQuote(targetTable, sourceTable,
                periodKey, start, end);
            log.info("K线聚合完成：{} <- {}，{}~{}，影响 {} 行", targetTable, sourceTable,
                start, end, rows);
        } catch (Exception e) {
            log.error("K线聚合失败：table={}, periodKey={}", targetTable, periodKey, e);
        }
    }

    private LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private LocalDate monthStart(LocalDate date) {
        return date.withDayOfMonth(1);
    }

    private LocalDate quarterStart(LocalDate date) {
        int month = (date.getMonthValue() - 1) / 3 * 3 + 1;
        return LocalDate.of(date.getYear(), month, 1);
    }

    private LocalDate yearStart(LocalDate date) {
        return LocalDate.of(date.getYear(), 1, 1);
    }
}
