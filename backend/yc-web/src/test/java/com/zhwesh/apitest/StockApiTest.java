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
        var first = response.path("data").path("records").get(0);
        assertThat(first.path("code").asString()).isNotBlank();
        assertThat(first.path("name").asString()).isNotBlank();

        var secondPage = api.stocks(null, 2, 5);
        assertThat(secondPage.path("data").path("records")).hasSize(5);
        assertThat(secondPage.path("data").path("records").get(0).path("code").asString())
            .isNotEqualTo(first.path("code").asString());
    }

    @Test
    void searchStocks() {
        var response = api.stocks("浦发", 1, 5);
        assertThat(response.path("code").asInt()).isEqualTo(1);
        assertThat(response.path("data").path("total").asLong()).isEqualTo(1);
        assertThat(response.path("data").path("records").get(0).path("code").asString())
            .isEqualTo("600000");
        assertThat(response.path("data").path("records").get(0).path("name").asString())
            .isEqualTo("浦发银行");
    }

    @Test
    void stockDetail() {
        var response = api.stockDetail("600000");
        assertThat(response.path("code").asInt()).isEqualTo(1);
        var data = response.path("data");
        assertThat(data.path("code").asString()).isEqualTo("600000");
        assertThat(data.path("name").asString()).isEqualTo("浦发银行");
        assertThat(data.path("board").asString()).isEqualTo("主板");
        assertThat(data.path("listDate").asString()).isNotBlank();
        assertThat(data.path("totalShares").asLong()).isPositive();
        assertThat(data.path("circulateShares").asLong()).isPositive();
        assertThat(data.path("initialPrice").asDouble()).isPositive();
        assertThat(data.path("bookValue").isNull()).isTrue();
        assertThat(data.path("eps").isNull()).isTrue();
    }
}
