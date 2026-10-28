package com.zhwesh.apitest;

import com.zhwesh.apitest.support.ApiTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 成交记录接口测试
 */
class TradeApiTest extends ApiTestBase {

    @Test
    void listTrades() {
        var response = api.trades(1L, null, 1, 5);
        assertThat(response.path("code").asInt()).isEqualTo(1);
        assertThat(response.path("data").path("total").asLong()).isEqualTo(20);
        assertThat(response.path("data").path("records")).hasSize(5);

        var first = response.path("data").path("records").get(0);
        assertThat(first.path("orderId").asLong()).isEqualTo(1);
        assertThat(first.path("accountId").asLong()).isEqualTo(1);
        assertThat(first.path("stockCode").asString()).isEqualTo("000001");
        assertThat(first.path("stockName").asString()).isEqualTo("平安银行");
        assertThat(first.path("price").asDouble()).isCloseTo(11.50, within(0.001));
        assertThat(first.path("quantity").asInt()).isEqualTo(100);
        assertThat(first.path("amount").asDouble()).isCloseTo(1150.0, within(0.01));
        assertThat(first.path("createTime").asString()).contains("T");

        var filtered = api.trades(1L, "000001", 1, 20);
        assertThat(filtered.path("data").path("total").asLong()).isEqualTo(10);
        assertThat(filtered.path("data").path("records")).allSatisfy(record -> {
            assertThat(record.path("stockCode").asString()).isEqualTo("000001");
            assertThat(record.path("amount").asDouble())
                .isCloseTo(record.path("price").asDouble() * record.path("quantity").asInt(), within(0.01));
        });
    }
}
