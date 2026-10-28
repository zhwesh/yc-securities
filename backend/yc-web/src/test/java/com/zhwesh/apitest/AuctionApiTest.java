package com.zhwesh.apitest;

import com.zhwesh.apitest.support.ApiClient;
import com.zhwesh.apitest.support.ApiTestBase;
import com.zhwesh.engine.auction.CallAuctionEngine;
import com.zhwesh.service.CallAuctionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 集合竞价接口测试
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "trading.always-on=false",
        "trading.call-auction-enabled=true",
        "trading.test-now=2026-10-22T09:16:00",
        "trading.snapshot-enabled=false",
        "trading.kline-aggregation-enabled=false"
    })
@ActiveProfiles({"dev", "test"})
class AuctionApiTest extends ApiTestBase {

    @Autowired
    private CallAuctionService callAuctionService;

    @Autowired
    private CallAuctionEngine callAuctionEngine;

    @BeforeEach
    void clearAuction() {
        callAuctionEngine.clearAll();
    }

    @Test
    void openingAuctionMatch() {
        JsonNode quote = api.quote("000001");
        BigDecimal base = new BigDecimal(quote.path("data").path("price").asString());
        BigDecimal buyPrice = base.add(new BigDecimal("0.20")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal sellPrice = base.subtract(new BigDecimal("0.20")).setScale(2, RoundingMode.HALF_UP);

        JsonNode buy = api.createOrder(orderBody(buyPrice, 100, "910001"));
        JsonNode sell = api.createOrder(orderBody(sellPrice, -100, "910002"));
        assertThat(buy.path("code").asInt()).isEqualTo(1);
        assertThat(sell.path("code").asInt()).isEqualTo(1);
        assertThat(buy.path("data").path("status").asInt()).isEqualTo(1);
        assertThat(sell.path("data").path("status").asInt()).isEqualTo(1);

        JsonNode auction = api.auction("000001");
        assertThat(auction.path("code").asInt()).isEqualTo(1);
        JsonNode data = auction.path("data");
        assertThat(data.path("phase").asInt()).isEqualTo(1);
        assertThat(data.path("matchedVolume").asDouble()).isCloseTo(1.00, within(0.0001));
        assertThat(data.path("indicativePrice").asDouble()).isBetween(
            sellPrice.doubleValue(), buyPrice.doubleValue());
        assertThat(data.path("bids").size()).isPositive();
        assertThat(data.path("asks").size()).isPositive();

        callAuctionService.closeOpeningAuction(LocalDateTime.of(2026, 10, 22, 9, 25, 0));

        assertThat(api.orderDetail(buy.path("data").path("id").asLong())
            .path("data").path("status").asInt()).isEqualTo(3);
        assertThat(api.orderDetail(sell.path("data").path("id").asLong())
            .path("data").path("status").asInt()).isEqualTo(3);
        JsonNode trades = api.trades(1L, "000001", 1, 20);
        assertThat(trades.path("code").asInt()).isEqualTo(1);
        long buyId = buy.path("data").path("id").asLong();
        long sellId = sell.path("data").path("id").asLong();
        Integer auctionTrades = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sold_orders WHERE order_id IN (?, ?)",
            Integer.class, buyId, sellId);
        assertThat(auctionTrades).isEqualTo(2);
        var tradeRows = jdbcTemplate.queryForList(
            "SELECT quantity, taker_side FROM sold_orders WHERE order_id IN (?, ?) ORDER BY quantity DESC",
            buyId, sellId);
        assertThat(tradeRows).hasSize(2);
        assertThat(((Number) tradeRows.get(0).get("quantity")).intValue()).isEqualTo(100);
        assertThat(((Number) tradeRows.get(1).get("quantity")).intValue()).isEqualTo(-100);
        assertThat(((Number) tradeRows.get(0).get("taker_side")).intValue()).isZero();
        assertThat(((Number) tradeRows.get(1).get("taker_side")).intValue()).isZero();

        JsonNode marketTrade = api.marketTrades("000001", 50).path("data").get(0);
        assertThat(marketTrade.path("price").asDouble())
            .isCloseTo(data.path("indicativePrice").asDouble(), within(0.0001));
        assertThat(marketTrade.path("volume").asLong()).isEqualTo(100);
        assertThat(marketTrade.path("direction").asInt()).isZero();

        matchEngine.clearTrades();
        JsonNode persistedTrades = api.marketTrades("000001", 50);
        assertThat(persistedTrades.path("data").size()).isPositive();
        assertThat(persistedTrades.path("data").get(0).path("direction").asInt()).isIn(0, 1);
    }

    @Test
    void cancelAuctionOrderInCancelablePhase() {
        JsonNode quote = api.quote("000001");
        BigDecimal base = new BigDecimal(quote.path("data").path("price").asString());
        BigDecimal price = base.add(new BigDecimal("0.30")).setScale(2, RoundingMode.HALF_UP);
        JsonNode order = api.createOrder(orderBody(price, 100, "910003"));
        assertThat(order.path("code").asInt()).isEqualTo(1);

        JsonNode cancel = api.cancelOrder(1L, order.path("data").path("id").asLong());
        assertThat(cancel.path("code").asInt()).isEqualTo(1);
        assertThat(api.orderDetail(order.path("data").path("id").asLong())
            .path("data").path("status").asInt()).isEqualTo(4);
    }

    @Test
    void marketOrderRejectedInAuction() {
        JsonNode response = api.createOrder(ApiClient.body(
            "accountId", 1L, "code", "000001", "type", 2,
            "quantity", 100, "orderKey", "910006"));

        assertThat(response.path("code").asInt()).isZero();
        assertThat(response.path("msg").asString()).contains("仅支持限价");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM orders WHERE order_key = '910006'", Integer.class)).isZero();
    }

    @Test
    void openingResidualReleasedToContinuous() {
        JsonNode quote = api.quote("000001");
        BigDecimal base = new BigDecimal(quote.path("data").path("price").asString());
        BigDecimal buyPrice = base.add(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal sellPrice = base.add(new BigDecimal("0.20")).setScale(2, RoundingMode.HALF_UP);

        JsonNode buy = api.createOrder(orderBody(buyPrice, 100, "910004"));
        JsonNode sell = api.createOrder(orderBody(sellPrice, -100, "910005"));
        assertThat(buy.path("code").asInt()).isEqualTo(1);
        assertThat(sell.path("code").asInt()).isEqualTo(1);

        callAuctionService.closeOpeningAuction(LocalDateTime.of(2026, 10, 22, 9, 25, 0));
        callAuctionService.releaseOpeningOrders();

        assertThat(api.orderDetail(buy.path("data").path("id").asLong())
            .path("data").path("status").asInt()).isEqualTo(1);
        assertThat(api.orderDetail(sell.path("data").path("id").asLong())
            .path("data").path("status").asInt()).isEqualTo(1);

        assertThat(api.cancelOrder(1L, buy.path("data").path("id").asLong())
            .path("code").asInt()).isEqualTo(1);
        assertThat(api.cancelOrder(1L, sell.path("data").path("id").asLong())
            .path("code").asInt()).isEqualTo(1);
    }

    private Map<String, Object> orderBody(BigDecimal price, int quantity, String orderKey) {
        return ApiClient.body(
            "accountId", 1L,
            "code", "000001",
            "type", 1,
            "price", price,
            "quantity", quantity,
            "orderKey", orderKey);
    }
}
