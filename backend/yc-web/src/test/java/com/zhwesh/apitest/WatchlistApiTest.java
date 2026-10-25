package com.zhwesh.apitest;

import com.zhwesh.apitest.support.ApiClient;
import com.zhwesh.apitest.support.ApiTestBase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自选股接口测试
 */
class WatchlistApiTest extends ApiTestBase {

    @Test
    void listEmpty() {
        var response = api.watchlist();
        assertThat(response.path("code").asInt()).isEqualTo(1);
        assertThat(response.path("data").size()).isZero();
    }

    @Test
    void addAndList() {
        assertThat(api.addWatchlist("600000").path("code").asInt()).isEqualTo(1);
        assertThat(api.addWatchlist("000001").path("code").asInt()).isEqualTo(1);

        var response = api.watchlist();
        assertThat(response.path("code").asInt()).isEqualTo(1);
        assertThat(response.path("data").size()).isEqualTo(2);
        assertThat(response.path("data").get(0).path("code").asString()).isEqualTo("600000");
        assertThat(response.path("data").get(0).path("name").asString()).isEqualTo("浦发银行");
        assertThat(response.path("data").get(1).path("code").asString()).isEqualTo("000001");
        assertThat(response.path("data").get(1).path("name").asString()).isEqualTo("平安银行");
    }

    @Test
    void addDuplicate() {
        assertThat(api.addWatchlist("600000").path("code").asInt()).isEqualTo(1);

        var response = api.addWatchlist("600000");
        assertThat(response.path("code").asInt()).isZero();
        assertThat(response.path("msg").asString()).contains("已在自选中");
    }

    @Test
    void addStockNotFound() {
        var response = api.addWatchlist("999999");
        assertThat(response.path("code").asInt()).isZero();
        assertThat(response.path("msg").asString()).contains("股票不存在");
    }

    @Test
    void deleteWatchlist() {
        assertThat(api.addWatchlist("600000").path("code").asInt()).isEqualTo(1);
        assertThat(api.deleteWatchlist("600000").path("code").asInt()).isEqualTo(1);

        var response = api.watchlist();
        assertThat(response.path("code").asInt()).isEqualTo(1);
        assertThat(response.path("data").size()).isZero();
    }

    @Test
    void deleteNotInList() {
        var response = api.deleteWatchlist("600000");
        assertThat(response.path("code").asInt()).isZero();
        assertThat(response.path("msg").asString()).contains("不在自选中");
    }

    @Test
    void updateOrder() {
        assertThat(api.addWatchlist("600000").path("code").asInt()).isEqualTo(1);
        assertThat(api.addWatchlist("000001").path("code").asInt()).isEqualTo(1);
        assertThat(api.addWatchlist("300750").path("code").asInt()).isEqualTo(1);

        var response = api.updateWatchlist(List.of("300750", "600000", "000001"));
        assertThat(response.path("code").asInt()).isEqualTo(1);

        var list = api.watchlist().path("data");
        assertThat(list.size()).isEqualTo(3);
        assertThat(list.get(0).path("code").asString()).isEqualTo("300750");
        assertThat(list.get(1).path("code").asString()).isEqualTo("600000");
        assertThat(list.get(2).path("code").asString()).isEqualTo("000001");
    }

    @Test
    void updateEmptyClears() {
        assertThat(api.addWatchlist("600000").path("code").asInt()).isEqualTo(1);

        var response = api.updateWatchlist(List.of());
        assertThat(response.path("code").asInt()).isEqualTo(1);
        assertThat(api.watchlist().path("data").size()).isZero();
    }

    @Test
    void updateDuplicate() {
        var response = api.updateWatchlist(List.of("600000", "600000"));
        assertThat(response.path("code").asInt()).isZero();
        assertThat(response.path("msg").asString()).contains("不能重复");
    }

    @Test
    void updateStockNotFound() {
        var response = api.updateWatchlist(List.of("600000", "999999"));
        assertThat(response.path("code").asInt()).isZero();
        assertThat(response.path("msg").asString()).contains("不存在的股票代码");
    }

    @Test
    void watchlistLimit() {
        List<String> codes = jdbcTemplate.queryForList(
            "SELECT code FROM stock ORDER BY code LIMIT 51", String.class);
        for (int i = 0; i < 50; i++) {
            assertThat(api.addWatchlist(codes.get(i)).path("code").asInt()).isEqualTo(1);
        }

        var response = api.addWatchlist(codes.get(50));
        assertThat(response.path("code").asInt()).isZero();
        assertThat(response.path("msg").asString()).contains("上限");
    }

    @Test
    void unauthorized() {
        var response = new ApiClient(port).watchlist();
        assertThat(response.path("code").asInt()).isZero();
        assertThat(response.path("msg").asString()).contains("未登录");
    }
}
