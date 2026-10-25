package com.zhwesh.apitest;

import com.zhwesh.apitest.support.ApiTestBase;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 资金账户接口测试
 */
class AccountApiTest extends ApiTestBase {

    @Test
    void getAccounts() {
        var response = api.accounts();
        assertThat(response.path("code").asInt()).isEqualTo(1);
        assertThat(response.path("data")).hasSize(1);
        assertThat(response.path("data").get(0).path("availableAssets").asDouble())
            .isCloseTo(1000000.0, within(0.01));
    }

    @Test
    void openAccount() {
        var response = api.openAccount("123456");
        assertThat(response.path("code").asInt()).isEqualTo(1);
        assertThat(api.accounts().path("data")).hasSize(2);
    }

    @Test
    void openBoard() {
        // 已开通板块重复开通幂等成功
        assertThat(api.openBoard(1L, List.of("创业板")).path("code").asInt()).isEqualTo(1);
        // 新账户资产为 0，开创业板应失败
        api.openAccount("123456");
        assertThat(api.openBoard(2L, List.of("创业板")).path("code").asInt()).isEqualTo(0);
    }

    @Test
    void updateTradePassword() {
        assertThat(api.updateTradePassword(1L, "123456", "654321").path("code").asInt())
            .isEqualTo(1);
        assertThat(api.verifyTradePassword(1L, "654321", 24).path("code").asInt())
            .isEqualTo(1);
        assertThat(api.updateTradePassword(1L, "654321", "123456").path("code").asInt())
            .isEqualTo(1);
    }

    @Test
    void verifyTradePassword() {
        assertThat(api.verifyTradePassword(1L, "123456", 24).path("code").asInt())
            .isEqualTo(1);
        assertThat(api.verifyTradePassword(1L, "bad-password", 24).path("code").asInt())
            .isEqualTo(0);
    }

    @Test
    void deposit() {
        var response = api.deposit(1L, new BigDecimal("1000"));
        assertThat(response.path("code").asInt()).isEqualTo(1);
        assertThat(api.accounts().path("data").get(0).path("availableAssets").asDouble())
            .isCloseTo(1001000.0, within(0.01));
    }

    @Test
    void withdraw() {
        // 未验证交易密码时拒绝
        assertThat(api.withdraw(1L, new BigDecimal("500")).path("code").asInt()).isEqualTo(0);
        assertThat(api.verifyTradePassword(1L, "123456", 24).path("code").asInt()).isEqualTo(1);
        assertThat(api.withdraw(1L, new BigDecimal("500")).path("code").asInt()).isEqualTo(1);
        assertThat(api.accounts().path("data").get(0).path("availableAssets").asDouble())
            .isCloseTo(999500.0, within(0.01));
    }
}
