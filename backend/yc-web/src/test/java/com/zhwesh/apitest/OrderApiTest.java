package com.zhwesh.apitest;

import com.zhwesh.apitest.support.ApiClient;
import com.zhwesh.apitest.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 委托单接口测试
 */
class OrderApiTest extends ApiTestBase {

    @Test
    void createLimitBuyAndCancel() {
        JsonNode response = api.createOrder(ApiClient.body(
            "accountId", 1, "code", "600000", "type", 1,
            "price", new BigDecimal("9.50"), "quantity", 100, "orderKey", "100001"));
        assertThat(response.path("code").asInt()).isEqualTo(1);
        assertThat(response.path("data").path("status").asInt()).isEqualTo(1);
        assertThat(response.path("data").path("stockCode").asString()).isEqualTo("600000");
        assertThat(response.path("data").path("createTime").asString()).contains("T");

        // 冻结 = 950 + 手续费 0.25
        assertThat(api.accounts().path("data").get(0).path("availableAssets").asDouble())
            .isCloseTo(999049.75, within(0.01));
        JsonNode book = api.orderBook("600000", 5);
        assertThat(book.path("data").path("bids")).hasSize(1);
        assertThat(book.path("data").path("bids").get(0).path("price").asDouble())
            .isCloseTo(9.50, within(0.001));

        long orderId = response.path("data").path("id").asLong();
        assertThat(api.cancelOrder(1L, orderId).path("code").asInt()).isEqualTo(1);
        assertThat(api.accounts().path("data").get(0).path("availableAssets").asDouble())
            .isCloseTo(1000000.0, within(0.01));
        assertThat(api.cancelOrder(1L, orderId).path("code").asInt()).isEqualTo(0);
    }

    @Test
    void createMarketBuy() {
        JsonNode response = api.createOrder(ApiClient.body(
            "accountId", 1, "code", "600000", "type", 2,
            "quantity", 100, "orderKey", "100002"));
        assertThat(response.path("code").asInt()).isEqualTo(1);
        // 无对手盘，市价单立即撤销
        assertThat(response.path("data").path("status").asInt()).isEqualTo(4);
        assertThat(api.accounts().path("data").get(0).path("availableAssets").asDouble())
            .isCloseTo(1000000.0, within(0.01));
    }

    @Test
    void sellWithoutPosition() {
        JsonNode response = api.createOrder(ApiClient.body(
            "accountId", 1, "code", "600000", "type", 1,
            "price", new BigDecimal("9.50"), "quantity", -100, "orderKey", "100003"));
        assertThat(response.path("code").asInt()).isEqualTo(0);
    }

    @Test
    void sellWithPosition() {
        JsonNode response = api.createOrder(ApiClient.body(
            "accountId", 1, "code", "000001", "type", 1,
            "price", new BigDecimal("11.50"), "quantity", -100, "orderKey", "100004"));
        assertThat(response.path("code").asInt()).isEqualTo(1);
        assertThat(response.path("data").path("status").asInt()).isEqualTo(1);
        api.verifyTradePassword(1L, "123456", 24);
        assertThat(api.positions(1L).path("data").get(0).path("availableQuantity").asLong())
            .isEqualTo(9900);
    }

    @Test
    void marketSellUnfreezesPosition() {
        JsonNode response = api.createOrder(ApiClient.body(
            "accountId", 1, "code", "000001", "type", 2,
            "quantity", -100, "orderKey", "100005"));
        assertThat(response.path("code").asInt()).isEqualTo(1);
        assertThat(response.path("data").path("status").asInt()).isEqualTo(4);
        api.verifyTradePassword(1L, "123456", 24);
        assertThat(api.positions(1L).path("data").get(0).path("availableQuantity").asLong())
            .isEqualTo(10000);
    }

    @Test
    void frozenAccountCannotCreateOrder() {
        jdbcTemplate.update("UPDATE account SET status = 2 WHERE id = 1");

        JsonNode response = api.createOrder(ApiClient.body(
            "accountId", 1, "code", "600000", "type", 1,
            "price", new BigDecimal("9.50"), "quantity", 100, "orderKey", "100006"));

        assertThat(response.path("code").asInt()).isEqualTo(0);
        jdbcTemplate.update("UPDATE account SET status = 1 WHERE id = 1");
    }

    /**
     * 重复幂等键返回原委托
     */
    @Test
    void duplicateOrderKeyReturnsExistingOrder() {
        JsonNode first = api.createOrder(ApiClient.body(
            "accountId", 1, "code", "600000", "type", 1,
            "price", new BigDecimal("9.50"), "quantity", 100,
            "orderKey", "idem-order-key-1"));
        JsonNode second = api.createOrder(ApiClient.body(
            "accountId", 1, "code", "600000", "type", 1,
            "price", new BigDecimal("9.50"), "quantity", 100,
            "orderKey", "idem-order-key-1"));

        assertThat(first.path("code").asInt()).isEqualTo(1);
        assertThat(second.path("code").asInt()).isEqualTo(1);
        assertThat(second.path("data").path("id").asLong())
            .isEqualTo(first.path("data").path("id").asLong());
    }

    @Test
    void listOrders() {
        JsonNode response = api.orders(1L, null, null, 1, 10);
        assertThat(response.path("code").asInt()).isEqualTo(1);
        assertThat(response.path("data").path("total").asLong()).isEqualTo(20);
        assertThat(response.path("data").path("records")).hasSize(10);

        JsonNode completed = api.orders(1L, null, 3, 1, 10);
        assertThat(completed.path("data").path("total").asLong()).isEqualTo(5);
        assertThat(completed.path("data").path("records")).hasSize(5);
    }

    @Test
    void orderDetail() {
        JsonNode response = api.orderDetail(1L);
        assertThat(response.path("code").asInt()).isEqualTo(1);
        assertThat(response.path("data").path("id").asLong()).isEqualTo(1);
        assertThat(api.orderDetail(99999L).path("code").asInt()).isEqualTo(0);
    }
}
