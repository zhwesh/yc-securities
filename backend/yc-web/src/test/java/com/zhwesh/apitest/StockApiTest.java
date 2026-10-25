package com.zhwesh.apitest;

import com.zhwesh.apitest.support.ApiTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 股票信息接口测试
 */
class StockApiTest extends ApiTestBase {

    @Test
    void listStocks() {
        var response = api.stocks(null, 1, 5);
        assertThat(response.path("code").asInt()).isEqualTo(1);
        assertThat(response.path("data").path("total").asLong()).isEqualTo(5624);
        assertThat(response.path("data").path("records")).hasSize(5);
    }

    @Test
    void searchStocks() {
        var response = api.stocks("浦发", 1, 5);
        assertThat(response.path("code").asInt()).isEqualTo(1);
        assertThat(response.path("data").path("total").asLong()).isEqualTo(1);
        assertThat(response.path("data").path("records").get(0).path("code").asString())
            .isEqualTo("600000");
    }

    @Test
    void stockDetail() {
        var response = api.stockDetail("600000");
        assertThat(response.path("code").asInt()).isEqualTo(1);
        var data = response.path("data");
        assertThat(data.path("name").asString()).isEqualTo("浦发银行");
        assertThat(data.path("board").asString()).isEqualTo("主板");
        assertThat(data.path("bookValue").isNull()).isTrue();
        assertThat(data.path("eps").isNull()).isTrue();
    }
}
