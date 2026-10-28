package com.zhwesh.apitest;

import com.zhwesh.apitest.support.ApiTestBase;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
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
        var account = response.path("data").get(0);
        assertThat(account.path("id").asLong()).isEqualTo(1);
        assertThat(account.path("availableAssets").asDouble())
            .isCloseTo(1000000.0, within(0.01));
        assertThat(account.path("status").asInt()).isEqualTo(1);
        assertThat(account.path("createTime").asString()).contains("T");
        assertThat(strings(account.path("boards")))
            .containsExactlyInAnyOrder("主板", "创业板", "科创板", "北交所");
    }

    @Test
    void openAccount() {
        var response = api.openAccount("123456");
        assertThat(response.path("code").asInt()).isEqualTo(1);
        var accounts = api.accounts().path("data");
        assertThat(accounts).hasSize(2);
        var account = accounts.get(1);
        assertThat(account.path("id").asLong()).isGreaterThan(1);
        assertThat(account.path("availableAssets").asDouble()).isZero();
        assertThat(account.path("status").asInt()).isEqualTo(1);
        assertThat(account.path("createTime").asString()).contains("T");
        assertThat(strings(account.path("boards"))).containsExactly("主板");
    }

    @Test
    void openBoard() {
        assertThat(api.openBoard(1L, List.of("创业板")).path("code").asInt()).isEqualTo(1);
        api.openAccount("123456");
        var response = api.openBoard(2L, List.of("创业板"));
        assertThat(response.path("code").asInt()).isEqualTo(0);
        assertThat(response.path("msg").asString()).contains("未达到开通条件");
        assertThat(api.accounts().path("data").get(1).path("boards").toString())
            .contains("主板");
    }

    @Test
    void updateTradePassword() {
        assertThat(api.updateTradePassword(1L, "123456", "654321").path("code").asInt())
            .isEqualTo(1);
        assertThat(api.positions(1L).path("code").asInt()).isZero();
        assertThat(api.verifyTradePassword(1L, "123456", 24).path("code").asInt())
            .isZero();
        assertThat(api.verifyTradePassword(1L, "654321", 24).path("code").asInt())
            .isEqualTo(1);
        assertThat(api.updateTradePassword(1L, "654321", "123456").path("code").asInt())
            .isEqualTo(1);
        assertThat(api.positions(1L).path("code").asInt()).isZero();
        assertThat(api.verifyTradePassword(1L, "654321", 24).path("code").asInt())
            .isZero();
        assertThat(api.verifyTradePassword(1L, "123456", 24).path("code").asInt())
            .isEqualTo(1);
    }

    @Test
    void verifyTradePassword() {
        assertThat(api.verifyTradePassword(1L, "123456", 24).path("code").asInt())
            .isEqualTo(1);
        assertThat(api.positions(1L).path("code").asInt()).isEqualTo(1);
        assertThat(api.verifyTradePassword(1L, "bad-password", 24).path("code").asInt())
            .isEqualTo(0);
    }

    @Test
    void deposit() {
        var response = api.deposit(1L, new BigDecimal("1000"));
        assertThat(response.path("code").asInt()).isEqualTo(1);
        assertThat(api.accounts().path("data").get(0).path("availableAssets").asDouble())
            .isCloseTo(1001000.0, within(0.01));
        assertThat(api.deposit(1L, new BigDecimal("-1")).path("code").asInt()).isZero();
        assertThat(api.accounts().path("data").get(0).path("availableAssets").asDouble())
            .isCloseTo(1001000.0, within(0.01));
    }

    @Test
    void withdraw() {
        assertThat(api.withdraw(1L, new BigDecimal("500")).path("code").asInt()).isEqualTo(0);
        assertThat(api.verifyTradePassword(1L, "123456", 24).path("code").asInt()).isEqualTo(1);
        assertThat(api.withdraw(1L, new BigDecimal("2000000")).path("code").asInt()).isZero();
        assertThat(api.accounts().path("data").get(0).path("availableAssets").asDouble())
            .isCloseTo(1000000.0, within(0.01));
        assertThat(api.withdraw(1L, new BigDecimal("500")).path("code").asInt()).isEqualTo(1);
        assertThat(api.accounts().path("data").get(0).path("availableAssets").asDouble())
            .isCloseTo(999500.0, within(0.01));
    }

    /**
     * 获取JSON数组字符串列表
     * @param node JSON数组
     * @return 字符串列表
     */
    private List<String> strings(tools.jackson.databind.JsonNode node) {
        List<String> values = new ArrayList<>();
        node.forEach(value -> values.add(value.asString()));
        return values;
    }
}
