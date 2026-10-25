package com.zhwesh.apitest;

import com.zhwesh.apitest.support.ApiTestBase;
import com.zhwesh.service.KLineAggregationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 周/月/季/年K线聚合测试
 */
class KLineAggregationTest extends ApiTestBase {

    @Autowired
    private KLineAggregationService kLineAggregationService;

    @Test
    void aggregateLatestPeriods() {
        kLineAggregationService.aggregateLatestPeriods();

        LocalDate latest = jdbcTemplate.queryForObject(
            "SELECT MAX(date) FROM daily_quote WHERE stock_code='600000'", LocalDate.class);
        assertThat(latest).isNotNull();

        List<String> tables = List.of("week_quote", "month_quote", "quarter_quote", "year_quote");
        for (String table : tables) {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE stock_code='600000' AND date=?",
                Integer.class, latest);
            assertThat(count).as(table).isPositive();
        }

        assertWeekAggregate(latest);

        // 幂等：重复执行不会产生重复行
        Integer before = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM week_quote WHERE stock_code='600000'", Integer.class);
        kLineAggregationService.aggregateLatestPeriods();
        Integer after = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM week_quote WHERE stock_code='600000'", Integer.class);
        assertThat(after).isEqualTo(before);
    }

    @Test
    void aggregatePreviousPeriods() {
        kLineAggregationService.aggregateLatestPeriods();
        kLineAggregationService.aggregatePreviousPeriods();

        LocalDate latest = jdbcTemplate.queryForObject(
            "SELECT MAX(date) FROM daily_quote WHERE stock_code='600000'", LocalDate.class);
        assertThat(latest).isNotNull();
        String previousMonthKey = String.format("%d-%02d",
            latest.minusMonths(1).getYear(), latest.minusMonths(1).getMonthValue());
        Integer monthCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM month_quote WHERE stock_code='600000' AND period_key=?",
            Integer.class, previousMonthKey);
        assertThat(monthCount).isPositive();
    }

    /**
     * 校验周K的 OHLC/成交量与区间日线聚合结果一致
     */
    private void assertWeekAggregate(LocalDate latest) {
        LocalDate weekStart = latest.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        List<Map<String, Object>> dailies = jdbcTemplate.queryForList(
            "SELECT open, high, low, close, volume FROM daily_quote " +
                "WHERE stock_code='600000' AND date BETWEEN ? AND ? ORDER BY date",
            weekStart, weekEnd);
        assertThat(dailies).isNotEmpty();

        Map<String, Object> week = jdbcTemplate.queryForMap(
            "SELECT open, high, low, close, volume FROM week_quote " +
                "WHERE stock_code='600000' AND date=?", latest);

        BigDecimal expectedOpen = (BigDecimal) dailies.get(0).get("open");
        BigDecimal expectedClose = (BigDecimal) dailies.get(dailies.size() - 1).get("close");
        BigDecimal expectedHigh = dailies.stream()
            .map(row -> (BigDecimal) row.get("high"))
            .max(BigDecimal::compareTo)
            .orElseThrow();
        BigDecimal expectedLow = dailies.stream()
            .map(row -> (BigDecimal) row.get("low"))
            .min(BigDecimal::compareTo)
            .orElseThrow();
        long expectedVolume = dailies.stream()
            .mapToLong(row -> ((Number) row.get("volume")).longValue())
            .sum();

        assertThat((BigDecimal) week.get("open")).isEqualByComparingTo(expectedOpen);
        assertThat((BigDecimal) week.get("close")).isEqualByComparingTo(expectedClose);
        assertThat((BigDecimal) week.get("high")).isEqualByComparingTo(expectedHigh);
        assertThat((BigDecimal) week.get("low")).isEqualByComparingTo(expectedLow);
        assertThat(((Number) week.get("volume")).longValue()).isEqualTo(expectedVolume);
    }
}
