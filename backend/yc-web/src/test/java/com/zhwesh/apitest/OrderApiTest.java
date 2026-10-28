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
        var order = response.path("data");
        assertThat(order.path("id").asLong()).isPositive();
        assertThat(order.path("accountId").asLong()).isEqualTo(1);
        assertThat(order.path("stockCode").asString()).isEqualTo("600000");
        assertThat(order.path("stockName").asString()).isEqualTo("浦发银行");
        assertThat(order.path("price").asDouble()).isCloseTo(9.50, within(0.001));
        assertThat(order.path("quantity").asInt()).isEqualTo(100);
        assertThat(order.path("soldQuantity").asInt()).isZero();
        assertThat(order.path("type").asInt()).isEqualTo(1);
        assertThat(order.path("status").asInt()).isEqualTo(1);
        assertThat(order.path("createTime").asString()).contains("T");

        // 冻结 = 950 + 手续费 0.25
        assertThat(api.accounts().path("data").get(0).path("availableAssets").asDouble())
            .isCloseTo(999049.75, within(0.01));
        JsonNode book = api.orderBook("600000", 5);
        assertThat(book.path("data").path("bids")).hasSize(1);
        assertThat(book.path("data").path("bids").get(0).path("price").asDouble())
            .isCloseTo(9.50, within(0.001));

        long orderId = order.path("id").asLong();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM orders WHERE id = ?", Integer.class, orderId)).isEqualTo(1);
        assertThat(api.cancelOrder(1L, orderId).path("code").asInt()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM orders WHERE id = ?", Integer.class, orderId)).isEqualTo(4);
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
        assertThat(response.path("data").path("type").asInt()).isEqualTo(2);
        assertThat(response.path("data").path("quantity").asInt()).isEqualTo(100);
        assertThat(response.path("data").path("soldQuantity").asInt()).isZero();
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
        assertThat(response.path("data").path("quantity").asInt()).isEqualTo(-100);
        assertThat(response.path("data").path("soldQuantity").asInt()).isZero();
        api.verifyTradePassword(1L, "123456", 24);
        assertThat(api.positions(1L).path("data").get(0).path("availableQuantity").asLong())
            .isEqualTo(9900);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT available_quantity FROM `position` WHERE account_id = 1 AND stock_code = '000001'",
            Long.class)).isEqualTo(9900L);
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
        assertThat(response.path("msg").asString()).contains("冻结");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM orders WHERE order_key = '100006'", Integer.class)).isZero();
        jdbcTemplate.update("UPDATE account SET status = 1 WHERE id = 1");
    }

    /**
     * 重复幂等键返回原委托
     */
    @Test
    void rejectInvalidOrderFields() {
        assertThat(api.createOrder(ApiClient.body(
            "accountId", 1, "code", "600000", "type", 1,
            "price", new BigDecimal("9.50"), "quantity", 50, "orderKey", "100007"))
            .path("code").asInt()).isZero();
        assertThat(api.createOrder(ApiClient.body(
            "accountId", 1, "code", "600000", "type", 1,
            "price", BigDecimal.ZERO, "quantity", 100, "orderKey", "100008"))
            .path("code").asInt()).isZero();
        assertThat(api.createOrder(ApiClient.body(
            "accountId", 1, "code", "600000", "type", 3,
            "price", new BigDecimal("9.50"), "quantity", 100, "orderKey", "100009"))
            .path("code").asInt()).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM orders WHERE order_key IN ('100007','100008','100009')",
            Integer.class)).isZero();
    }

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
        assertThat(second.path("data").path("accountId").asLong()).isEqualTo(1);
        assertThat(second.path("data").path("stockCode").asString()).isEqualTo("600000");
        assertThat(second.path("data").path("price").asDouble()).isCloseTo(9.50, within(0.001));
        assertThat(second.path("data").path("quantity").asInt()).isEqualTo(100);
        assertThat(second.path("data").path("status").asInt()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM orders WHERE order_key = 'idem-order-key-1'",
            Integer.class)).isEqualTo(1);
    }

    @Test
    void listOrders() {
        JsonNode response = api.orders(1L, null, null, 1, 10);
        assertThat(response.path("code").asInt()).isEqualTo(1);
        assertThat(response.path("data").path("total").asLong()).isEqualTo(20);
        assertThat(response.path("data").path("records")).hasSize(10);
        var first = response.path("data").path("records").get(0);
        assertThat(first.path("accountId").asLong()).isEqualTo(1);
        assertThat(first.path("stockCode").asString()).isEqualTo("600000");
        assertThat(first.path("stockName").asString()).isEqualTo("浦发银行");
        assertThat(first.path("quantity").asInt()).isEqualTo(100);
        assertThat(first.path("soldQuantity").asInt()).isZero();
        assertThat(first.path("type").asInt()).isEqualTo(1);
        assertThat(first.path("status").asInt()).isEqualTo(1);
        assertThat(first.path("createTime").asString()).contains("T");

        JsonNode completed = api.orders(1L, null, 3, 1, 10);
        assertThat(completed.path("data").path("total").asLong()).isEqualTo(5);
        assertThat(completed.path("data").path("records")).hasSize(5);
        assertThat(completed.path("data").path("records")).allSatisfy(record -> {
            assertThat(record.path("status").asInt()).isEqualTo(3);
            assertThat(record.path("soldQuantity").asInt()).isEqualTo(100);
        });
    }

    @Test
    void orderDetail() {
        JsonNode response = api.orderDetail(1L);
        assertThat(response.path("code").asInt()).isEqualTo(1);
        var order = response.path("data");
        assertThat(order.path("id").asLong()).isEqualTo(1);
        assertThat(order.path("accountId").asLong()).isEqualTo(1);
        assertThat(order.path("stockCode").asString()).isEqualTo("600000");
        assertThat(order.path("stockName").asString()).isEqualTo("浦发银行");
        assertThat(order.path("price").asDouble()).isCloseTo(9.50, within(0.001));
        assertThat(order.path("quantity").asInt()).isEqualTo(100);
        assertThat(order.path("soldQuantity").asInt()).isZero();
        assertThat(api.orderDetail(99999L).path("code").asInt()).isEqualTo(0);
    }
}
