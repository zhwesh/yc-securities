package com.zhwesh.apitest;

import com.zhwesh.apitest.support.ApiTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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

        var filtered = api.trades(1L, "000001", 1, 20);
        assertThat(filtered.path("data").path("total").asLong()).isEqualTo(10);
    }
}
