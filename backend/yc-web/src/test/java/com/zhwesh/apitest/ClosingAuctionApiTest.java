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
 * 收盘集合竞价测试（固定 14:58，不可撤单阶段）
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "trading.always-on=false",
        "trading.call-auction-enabled=true",
        "trading.test-now=2026-10-22T14:58:00",
        "trading.snapshot-enabled=false",
        "trading.kline-aggregation-enabled=false"
    })
@ActiveProfiles({"dev", "test"})
class ClosingAuctionApiTest extends ApiTestBase {

    @Autowired
    private CallAuctionService callAuctionService;

    @Autowired
    private CallAuctionEngine callAuctionEngine;

    @BeforeEach
    void clearAuction() {
        callAuctionEngine.clearAll();
    }

    @Test
    void closingAuctionMatchAndCancelForbidden() {
        JsonNode quote = api.quote("000001");
        BigDecimal base = new BigDecimal(quote.path("data").path("price").asString());
        BigDecimal buyPrice = base.add(new BigDecimal("0.20")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal sellPrice = base.subtract(new BigDecimal("0.20")).setScale(2, RoundingMode.HALF_UP);

        JsonNode buy = api.createOrder(orderBody(buyPrice, 100, "920001"));
        JsonNode sell = api.createOrder(orderBody(sellPrice, -100, "920002"));
        assertThat(buy.path("code").asInt()).isEqualTo(1);
        assertThat(sell.path("code").asInt()).isEqualTo(1);

        JsonNode cancel = api.cancelOrder(1L, buy.path("data").path("id").asLong());
        assertThat(cancel.path("code").asInt()).isZero();
        assertThat(cancel.path("msg").asString()).contains("不可撤单");

        JsonNode auction = api.auction("000001");
        assertThat(auction.path("data").path("phase").asInt()).isEqualTo(5);
        assertThat(auction.path("data").path("matchedVolume").asDouble())
            .isCloseTo(1.00, within(0.0001));

        callAuctionService.closeClosingAuction(LocalDateTime.of(2026, 10, 22, 15, 0, 0));

        assertThat(api.orderDetail(buy.path("data").path("id").asLong())
            .path("data").path("status").asInt()).isEqualTo(3);
        assertThat(api.orderDetail(sell.path("data").path("id").asLong())
            .path("data").path("status").asInt()).isEqualTo(3);
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
