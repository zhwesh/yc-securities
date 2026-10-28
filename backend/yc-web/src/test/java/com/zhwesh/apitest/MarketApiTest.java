package com.zhwesh.apitest;

import com.zhwesh.apitest.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 行情接口测试
 */
class MarketApiTest extends ApiTestBase {

    @Test
    void quote() {
        JsonNode response = api.quote("600000");
        assertThat(response.path("code").asInt()).isEqualTo(1);
        JsonNode data = response.path("data");
        assertThat(data.path("code").asString()).isEqualTo("600000");
        assertThat(data.path("name").asString()).isEqualTo("浦发银行");
        assertThat(data.path("change").asDouble()).isZero();
        assertThat(data.path("changePercent").asDouble()).isZero();
        assertThat(data.path("amount").asDouble()).isZero();
        assertThat(data.path("price").asDouble()).isCloseTo(9.59, within(0.0001));
        assertThat(data.path("preClose").asDouble()).isCloseTo(9.59, within(0.0001));
        assertThat(data.path("volume").asDouble()).isZero();
        assertThat(data.path("open").isNull()).isTrue();
    }

    @Test
    void quotes() {
        JsonNode response = api.quotes("600000,000001,300750");
        assertThat(response.path("code").asInt()).isEqualTo(1);
        var data = response.path("data");
        assertThat(data).hasSize(3);
        assertThat(data.get(0).path("code").asString()).isEqualTo("600000");
        assertThat(data.get(0).path("name").asString()).isEqualTo("浦发银行");
        assertThat(data.get(1).path("code").asString()).isEqualTo("000001");
        assertThat(data.get(2).path("code").asString()).isEqualTo("300750");
        assertThat(data).allSatisfy(quote -> {
            assertThat(quote.path("price").asDouble()).isPositive();
            assertThat(quote.path("preClose").asDouble()).isPositive();
            assertThat(quote.path("volume").asDouble()).isGreaterThanOrEqualTo(0);
            assertThat(quote.path("amount").asDouble()).isGreaterThanOrEqualTo(0);
        });
    }

    @Test
    void orderBook() {
        JsonNode response = api.orderBook("600000", 5);
        assertThat(response.path("code").asInt()).isEqualTo(1);
        assertThat(response.path("data").path("bids")).isEmpty();
        assertThat(response.path("data").path("asks")).isEmpty();
    }

    @Test
    void rank() {
        JsonNode response = api.rank(2, 2, 1, 3);
        assertThat(response.path("code").asInt()).isEqualTo(1);
        assertThat(response.path("data").path("total").asLong()).isEqualTo(5624);
        var records = response.path("data").path("records");
        assertThat(records).hasSize(3);
        assertThat(records).allSatisfy(record -> {
            assertThat(record.path("code").asString()).isNotBlank();
            assertThat(record.path("name").asString()).isNotBlank();
            assertThat(record.path("price").asDouble()).isPositive();
            assertThat(record.path("changePercent").asDouble()).isNotNull();
        });
        for (int i = 1; i < records.size(); i++) {
            assertThat(records.get(i - 1).path("changePercent").asDouble())
                .isGreaterThanOrEqualTo(records.get(i).path("changePercent").asDouble());
        }
    }

    @Test
    void minute() {
        JsonNode response = api.minute("600000", null);
        assertThat(response.path("code").asInt()).isEqualTo(1);
        assertThat(response.path("data").path("minutes")).isEmpty();
        assertThat(response.path("data").path("preClose").asDouble()).isCloseTo(9.59, within(0.0001));
    }

    @Test
    void trades() {
        JsonNode response = api.marketTrades("600000", 50);
        assertThat(response.path("code").asInt()).isEqualTo(1);
        assertThat(response.path("data")).isEmpty();
    }

    @Test
    void indexes() {
        JsonNode response = api.indexes();
        assertThat(response.path("code").asInt()).isEqualTo(1);
        var data = response.path("data");
        assertThat(data).hasSize(5);
        assertThat(data.get(0).path("code").asString()).isEqualTo("SH000001");
        assertThat(data).allSatisfy(index -> {
            assertThat(index.path("code").asString()).isNotBlank();
            assertThat(index.path("name").asString()).isNotBlank();
            assertThat(index.path("price").asDouble()).isPositive();
            assertThat(index.path("volume").asDouble()).isZero();
            assertThat(index.path("amount").asDouble()).isZero();
        });
    }

    @Test
    void klineAdjust() {
        JsonNode raw = api.kline("600000", 1, 0, null, 1000);
        JsonNode qfq = api.kline("600000", 1, 1, null, 1000);
        JsonNode hfq = api.kline("600000", 1, 2, null, 1000);
        assertKLineInvariants(raw);
        assertKLineInvariants(qfq);
        assertKLineInvariants(hfq);

        double rawBefore = findBar(raw, "2024-05-31").path("close").asDouble();
        double qfqBefore = findBar(qfq, "2024-05-31").path("close").asDouble();
        double hfqBefore = findBar(hfq, "2024-05-31").path("close").asDouble();
        assertThat(qfqBefore).isCloseTo(rawBefore * 0.9, within(0.01));
        assertThat(hfqBefore).isCloseTo(rawBefore, within(0.01));

        double rawAfter = findBar(raw, "2024-06-04").path("close").asDouble();
        double qfqAfter = findBar(qfq, "2024-06-04").path("close").asDouble();
        double hfqAfter = findBar(hfq, "2024-06-04").path("close").asDouble();
        assertThat(qfqAfter).isCloseTo(rawAfter, within(0.01));
        assertThat(hfqAfter).isCloseTo(rawAfter / 0.9, within(0.01));
    }

    @Test
    void klineWeekAndMonth() {
        for (int period : new int[]{2, 3}) {
            JsonNode first = api.kline("600000", period, 0, null, 3);
            assertThat(first.path("code").asInt()).as(first.toString()).isEqualTo(1);
            JsonNode bars = first.path("data").path("kLine");
            assertThat(bars.size()).isEqualTo(3);
            assertThat(first.path("data").path("hasMore").asBoolean()).isTrue();
            assertKLineInvariants(first);

            String cursor = bars.get(0).path("time").asString();
            JsonNode older = api.kline("600000", period, 0, cursor, 3);
            assertThat(older.path("code").asInt()).isEqualTo(1);
            JsonNode olderBars = older.path("data").path("kLine");
            assertThat(olderBars.size()).isPositive();
            assertThat(olderBars.get(olderBars.size() - 1).path("time").asString())
                .isLessThan(cursor);
        }
    }

    @Test
    void klineQuarterAndYear() {
        for (int period : new int[]{4, 5}) {
            JsonNode response = api.kline("600000", period, 0, null, 1000);
            assertThat(response.path("code").asInt()).isEqualTo(1);
            JsonNode data = response.path("data");
            assertThat(data.path("period").asInt()).isEqualTo(period);
            assertThat(data.path("kLine").size()).isGreaterThan(0);
            assertKLineInvariants(response);
            JsonNode last = data.path("kLine").get(data.path("kLine").size() - 1);
            assertThat(last.path("time").asString()).matches("\\d{4}-\\d{2}-\\d{2}");
            assertThat(last.path("close").isNull()).isFalse();
        }
    }

    @Test
    void minuteKLine() {
        insertMinutes();

        JsonNode five = api.minuteKLine("600000", 5, null, 10);
        assertThat(five.path("code").asInt()).isEqualTo(1);
        JsonNode fiveBars = five.path("data").path("kLine");
        assertThat(fiveBars.size()).isEqualTo(1);
        JsonNode bar = fiveBars.get(0);
        assertThat(bar.path("time").asString()).isEqualTo("2026-10-13 09:30");
        assertThat(bar.path("open").asDouble()).isCloseTo(10.00, within(0.0001));
        assertThat(bar.path("high").asDouble()).isCloseTo(10.20, within(0.0001));
        assertThat(bar.path("low").asDouble()).isCloseTo(9.90, within(0.0001));
        assertThat(bar.path("close").asDouble()).isCloseTo(10.05, within(0.0001));
        assertThat(bar.path("volume").asDouble()).isCloseTo(150.00, within(0.0001));
        assertThat(bar.path("turnover").asDouble()).isCloseTo(150950.00, within(0.01));

        JsonNode oneMinute = api.minuteKLine("600000", 1, null, 3);
        assertThat(oneMinute.path("code").asInt()).isEqualTo(1);
        JsonNode oneBars = oneMinute.path("data").path("kLine");
        assertThat(oneBars.size()).isEqualTo(3);
        assertThat(oneMinute.path("data").path("hasMore").asBoolean()).isTrue();
        assertThat(oneBars.get(2).path("time").asString()).isEqualTo("2026-10-13 09:34");

        JsonNode older = api.minuteKLine("600000", 1, "2026-10-13 09:34:00", 3);
        assertThat(older.path("code").asInt()).isEqualTo(1);
        assertThat(older.path("data").path("kLine").size()).isEqualTo(3);
        assertThat(older.path("data").path("kLine").get(2).path("time").asString())
            .isEqualTo("2026-10-13 09:33");
    }

    @Test
    void invalidMinuteKLinePeriod() {
        JsonNode response = api.minuteKLine("600000", 7, null, 10);
        assertThat(response.path("code").asInt()).isZero();
        assertThat(response.path("msg").asString()).contains("不支持的分钟K线周期");
    }

    private void insertMinutes() {
        String[] prices = {"10.00", "10.10", "9.90", "10.20", "10.05"};
        int[] volumes = {1000, 2000, 3000, 4000, 5000};
        for (int i = 0; i < prices.length; i++) {
            BigDecimal price = new BigDecimal(prices[i]);
            BigDecimal amount = price.multiply(BigDecimal.valueOf(volumes[i]));
            jdbcTemplate.update("INSERT INTO minute_quote(stock_code, trade_time, price, volume, " +
                    "amount, avg_price) VALUES (?, ?, ?, ?, ?, ?)",
                "600000", LocalDateTime.parse("2026-10-13T09:3" + i + ":00"),
                price, volumes[i], amount, price);
        }
    }

    private JsonNode findBar(JsonNode kline, String date) {
        for (JsonNode bar : kline.path("data").path("kLine")) {
            if (date.equals(bar.path("time").asString())) {
                return bar;
            }
        }
        throw new AssertionError("K线数据缺少日期：" + date);
    }

    /**
     * 校验K线OHLC与成交量关系
     * @param response K线响应
     */
    private void assertKLineInvariants(JsonNode response) {
        var bars = response.path("data").path("kLine");
        assertThat(bars).isNotEmpty();
        String previousTime = "";
        for (JsonNode bar : bars) {
            String time = bar.path("time").asString();
            assertThat(time).isNotBlank();
            assertThat(time).isGreaterThan(previousTime);
            previousTime = time;
            double open = bar.path("open").asDouble();
            double high = bar.path("high").asDouble();
            double low = bar.path("low").asDouble();
            double close = bar.path("close").asDouble();
            assertThat(high).isGreaterThanOrEqualTo(open);
            assertThat(high).isGreaterThanOrEqualTo(close);
            assertThat(low).isLessThanOrEqualTo(open);
            assertThat(low).isLessThanOrEqualTo(close);
            assertThat(bar.path("volume").asDouble()).isGreaterThanOrEqualTo(0);
            if (!bar.path("turnover").isMissingNode() && !bar.path("turnover").isNull()) {
                assertThat(bar.path("turnover").asDouble()).isGreaterThanOrEqualTo(0);
            }
        }
    }
}
