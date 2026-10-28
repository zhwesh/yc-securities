package com.zhwesh.apitest;

import com.zhwesh.apitest.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import java.util.concurrent.TimeUnit;

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
        var card = response.path("data").get(0);
        assertThat(card.path("accountId").asLong()).isEqualTo(1);
        assertThat(card.path("cardNumber").asString()).isEqualTo(CARD_NUMBER);
        assertThat(card.path("bankName").asString()).isEqualTo("农业银行");
        assertThat(card.path("phone").asString()).isEqualTo("138****8000");
        assertThat(card.path("bindTime").asString()).contains("T");
    }

    @Test
    void bindBankCard() {
        String cardNumber = luhnCard("621700000000002");
        String code = sendAndReadCardCode(PHONE);
        var response = api.bindBankCard(1L, cardNumber, "建设银行", PHONE, code);
        assertThat(response.path("code").asInt()).isEqualTo(1);
        var cards = api.bankCards(1L).path("data");
        assertThat(cards).hasSize(2);
        var card = cards.get(1);
        assertThat(card.path("accountId").asLong()).isEqualTo(1);
        assertThat(card.path("cardNumber").asString()).isEqualTo(cardNumber);
        assertThat(card.path("bankName").asString()).isEqualTo("建设银行");
        assertThat(card.path("phone").asString()).isEqualTo("138****8000");
        assertThat(card.path("bindTime").asString()).contains("T");
    }

    @Test
    void unbindBankCard() {
        String code = sendAndReadCardCode(PHONE);
        var response = api.unbindBankCard(1L, CARD_NUMBER, PHONE, code);
        assertThat(response.path("code").asInt()).isEqualTo(1);
        assertThat(api.bankCards(1L).path("data")).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bank_card WHERE account_id = 1", Integer.class)).isZero();
    }

    @Test
    void sendCode() {
        assertThat(api.sendBankCardCode(PHONE).path("code").asInt()).isEqualTo(1);
        String code = redisTemplate.opsForValue().get("sms:code:" + PHONE);
        assertThat(code).matches("\\d{6}");
        Long ttl = redisTemplate.getExpire("sms:code:" + PHONE, TimeUnit.SECONDS);
        assertThat(ttl).isBetween(1L, 300L);
    }

    @Test
    void verifyCardPhone() {
        assertThat(api.verifyBankCard(CARD_NUMBER, PHONE).path("code").asInt()).isEqualTo(1);
        assertThat(api.verifyBankCard(CARD_NUMBER, "13800138001").path("code").asInt())
            .isEqualTo(0);
    }
}
