package com.zhwesh.apitest;

import com.zhwesh.apitest.support.ApiTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 银行卡接口测试
 */
class BankCardApiTest extends ApiTestBase {

    @Test
    void getBankCards() {
        var response = api.bankCards(1L);
        assertThat(response.path("code").asInt()).isEqualTo(1);
        assertThat(response.path("data")).hasSize(1);
        assertThat(response.path("data").get(0).path("phone").asString())
            .isEqualTo("138****8000");
    }

    @Test
    void bindBankCard() {
        String card = luhnCard("621700000000002");
        String code = sendAndReadCardCode(PHONE);
        var response = api.bindBankCard(1L, card, "建设银行", PHONE, code);
        assertThat(response.path("code").asInt()).isEqualTo(1);
        assertThat(api.bankCards(1L).path("data")).hasSize(2);
    }

    @Test
    void unbindBankCard() {
        String code = sendAndReadCardCode(PHONE);
        var response = api.unbindBankCard(1L, CARD_NUMBER, PHONE, code);
        assertThat(response.path("code").asInt()).isEqualTo(1);
        assertThat(api.bankCards(1L).path("data")).isEmpty();
    }

    @Test
    void sendCode() {
        assertThat(api.sendBankCardCode(PHONE).path("code").asInt()).isEqualTo(1);
        assertThat(redisTemplate.opsForValue().get("sms:code:" + PHONE)).isNotBlank();
    }

    @Test
    void verifyCardPhone() {
        assertThat(api.verifyBankCard(CARD_NUMBER, PHONE).path("code").asInt()).isEqualTo(1);
        assertThat(api.verifyBankCard(CARD_NUMBER, "13800138001").path("code").asInt())
            .isEqualTo(0);
    }
}
