package com.zhwesh.apitest;

import com.zhwesh.apitest.support.ApiClient;
import com.zhwesh.apitest.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 资金账户归属隔离测试
 */
class AuthorizationApiTest extends ApiTestBase {

    @Test
    void cannotAccessAnotherUsersAccount() {
        String phone = "13900000009";
        String code = sendAndReadUserCode(phone);
        JsonNode register = api.register(ApiClient.body(
            "phone", phone, "code", code, "username", "otheruser",
            "password", "123456", "name", "其他用户", "idNumber", "110101199001011234"));
        assertThat(register.path("code").asInt()).isEqualTo(1);
        assertThat(api.loginUsername("otheruser", "123456").path("code").asInt()).isEqualTo(1);
        assertThat(api.accounts().path("data")).isEmpty();

        assertThat(api.positions(1L).path("code").asInt()).isZero();
        assertThat(api.orders(1L, null, null, 1, 10).path("code").asInt()).isZero();
        assertThat(api.trades(1L, null, 1, 10).path("code").asInt()).isZero();
        assertThat(api.bankCards(1L).path("code").asInt()).isZero();
        assertThat(api.openBoard(1L, List.of("创业板")).path("code").asInt()).isZero();
        assertThat(api.deposit(1L, BigDecimal.ONE).path("code").asInt()).isZero();
        assertThat(api.withdraw(1L, BigDecimal.ONE).path("code").asInt()).isZero();
        assertThat(api.createOrder(ApiClient.body(
            "accountId", 1, "code", "600000", "type", 1,
            "price", new BigDecimal("9.50"), "quantity", 100, "orderKey", "auth-order"))
            .path("code").asInt()).isZero();
    }
}
