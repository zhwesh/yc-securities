package com.zhwesh.apitest;

import com.zhwesh.apitest.support.ApiTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 持仓接口测试
 */
class PositionApiTest extends ApiTestBase {

    @Test
    void listPositions() {
        api.verifyTradePassword(1L, "123456", 24);
        var response = api.positions(1L);
        assertThat(response.path("code").asInt()).isEqualTo(1);
        assertThat(response.path("data")).hasSize(1);

        var position = response.path("data").get(0);
        assertThat(position.path("code").asString()).isEqualTo("000001");
        assertThat(position.path("stockName").asString()).isEqualTo("平安银行");
        assertThat(position.path("quantity").asLong()).isEqualTo(10000);
        assertThat(position.path("availableQuantity").asLong()).isEqualTo(10000);
        assertThat(position.path("costPrice").asDouble()).isCloseTo(11.57, within(0.001));
        assertThat(position.path("currentPrice").asDouble()).isCloseTo(11.57, within(0.001));
        assertThat(position.path("marketValue").asDouble()).isCloseTo(115700.0, within(0.01));
        assertThat(position.path("profitLoss").asDouble()).isCloseTo(0.0, within(0.01));
    }
}
