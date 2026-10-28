package com.zhwesh.yc_securities;

import com.zhwesh.apitest.support.ApiClient;
import com.zhwesh.apitest.support.ApiTestBase;
import com.zhwesh.apitest.support.PerfMetrics;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 整体测试入口：跑完全部接口功能冒烟+吞吐量测量
 * 直接运行本类即可测试所有接口并生成 target/perf-report.csv
 */
class YcSecuritiesApplicationTests extends ApiTestBase {

    private static final int READ_CONCURRENCY = 32;
    private static final int READ_WARMUP = 100;
    private static final int READ_SAMPLES = 2000;
    private static final int WRITE_CONCURRENCY = 8;
    private static final int WRITE_WARMUP = 10;
    private static final int WRITE_SAMPLES = 200;

    private final AtomicInteger seq = new AtomicInteger();
    private final Object stateLock = new Object();

    @Test
    void runAllApisAndMeasureThroughput() {
        smokeAllApis();

        // 冒烟后重置为干净数据，再测吞吐
        resetDatabase();
        flushRedis();
        matchEngine.clearAllOrders();
        api = new ApiClient(port);
        api.loginUsername("admin", "123456");
        api.verifyTradePassword(1L, "123456", 24);

        PerfMetrics metrics = new PerfMetrics();
        measureReads(metrics);
        measureWrites(metrics);

        metrics.sortByName();
        metrics.print(System.out);
        metrics.writeCsv(Path.of("target", "perf-report.csv"));
    }

    // ================= 功能冒烟 =================

    private void smokeAllApis() {
        // 用户
        assertCode(api.sendUserCode(PHONE), 1, "GET /user/code");
        String code = redisTemplate.opsForValue().get("sms:code:" + PHONE);
        assertThat(code).isNotBlank();
        assertCode(api.loginUsername("admin", "123456"), 1, "POST /user/login/username");
        assertCode(api.loginPhone(PHONE, code), 1, "POST /user/login/phone");
        assertCode(api.currentUser(), 1, "GET /user");

        String updateCode = sendAndReadUserCode(PHONE);
        assertCode(api.updateUser(ApiClient.body("phone", PHONE, "code", updateCode)), 1,
            "PUT /user/update");
        JsonNode avatar = api.uploadAvatar(AVATAR_PNG);
        if (avatar.path("code").asInt() == 1) {
            assertThat(avatar.path("data").asString()).startsWith("https://");
        } else {
            System.out.println("[skip] 未配置 OSS 或上传失败：" + avatar);
        }

        String resetCode = sendAndReadUserCode(PHONE);
        assertCode(api.resetPassword(ApiClient.body("phone", PHONE, "code", resetCode,
            "newPassword", "123456")), 1, "PUT /user/password/reset");

        String registerPhone = "13900000002";
        String registerCode = sendAndReadUserCode(registerPhone);
        assertCode(api.register(ApiClient.body("phone", registerPhone, "code", registerCode,
            "username", "smoketest", "password", "123456", "name", "冒烟",
            "idNumber", "110101199001011234")), 1, "POST /user/register");

        // 资金账户
        assertCode(api.accounts(), 1, "GET /account");
        assertCode(api.openAccount("123456"), 1, "POST /account/open");
        assertCode(api.openBoard(1L, List.of("创业板")), 1, "PUT /account/board/open");
        assertCode(api.updateTradePassword(1L, "123456", "654321"), 1,
            "PUT /account/password/update");
        assertCode(api.verifyTradePassword(1L, "654321", 24), 1,
            "POST /account/password/verify");
        assertCode(api.updateTradePassword(1L, "654321", "123456"), 1,
            "PUT /account/password/update");
        assertCode(api.deposit(1L, new BigDecimal("1000")), 1, "PUT /account/deposit");
        assertCode(api.verifyTradePassword(1L, "123456", 24), 1,
            "POST /account/password/verify");
        assertCode(api.withdraw(1L, new BigDecimal("100")), 1, "PUT /account/withdraw");

        // 银行卡
        assertCode(api.bankCards(1L), 1, "GET /bank-card");
        String bindCard = luhnCard("621700000000002");
        String bindCode = sendAndReadCardCode(PHONE);
        assertCode(api.bindBankCard(1L, bindCard, "建设银行", PHONE, bindCode), 1,
            "POST /bank-card");
        assertCode(api.verifyBankCard(bindCard, PHONE), 1, "POST /bank-card/verify");
        String unbindCode = sendAndReadCardCode(PHONE);
        assertCode(api.unbindBankCard(1L, bindCard, PHONE, unbindCode), 1, "DELETE /bank-card");
        assertCode(api.sendBankCardCode(PHONE), 1, "GET /bank-card/code");

        // 股票/行情
        assertCode(api.stocks(null, 1, 10), 1, "GET /stock");
        assertCode(api.stockDetail("600000"), 1, "GET /stock/{code}");
        assertCode(api.quote("600000"), 1, "GET /market/quote");
        assertCode(api.quotes("600000,000001"), 1, "GET /market/quotes");
        assertCode(api.orderBook("600000", 5), 1, "GET /market/order-book");
        assertCode(api.rank(2, 2, 1, 10), 1, "GET /market/rank");
        assertCode(api.minute("600000", null), 1, "GET /market/minute");
        assertCode(api.marketTrades("600000", 50), 1, "GET /market/trades");
        assertCode(api.indexes(), 1, "GET /market/indexes");
        assertCode(api.kline("600000", 1, 1, null, 100), 1, "GET /market/kline");

        // 交易
        JsonNode buy = api.createOrder(ApiClient.body("accountId", 1, "code", "600000",
            "type", 1, "price", new BigDecimal("9.50"), "quantity", 100, "orderKey", "500001"));
        assertCode(buy, 1, "POST /order");
        long orderId = buy.path("data").path("id").asLong();
        assertCode(api.orders(1L, null, null, 1, 10), 1, "GET /order");
        assertCode(api.orderDetail(orderId), 1, "GET /order/{id}");
        assertCode(api.cancelOrder(1L, orderId), 1, "PUT /order/cancel");
        assertCode(api.trades(1L, null, 1, 10), 1, "GET /trade");
        assertCode(api.positions(1L), 1, "GET /position");
    }

    private void assertCode(JsonNode response, int expected, String apiName) {
        assertThat(response.path("code").asInt())
            .as("%s response=%s", apiName, response)
            .isEqualTo(expected);
    }

    // ================= 吞吐测量 =================

    private void measureReads(PerfMetrics metrics) {
        int c = READ_CONCURRENCY;
        int w = READ_WARMUP;
        int n = READ_SAMPLES;
        metrics.add(PerfMetrics.measure("GET /user", c, w, n, () -> api.currentUser()));
        metrics.add(PerfMetrics.measure("GET /stock", c, w, n, () -> api.stocks(null, 1, 10)));
        metrics.add(PerfMetrics.measure("GET /stock/{code}", c, w, n, () -> api.stockDetail("600000")));
        metrics.add(PerfMetrics.measure("GET /market/quote", c, w, n, () -> api.quote("600000")));
        metrics.add(PerfMetrics.measure("GET /market/quotes", c, w, n,
            () -> api.quotes("600000,000001,300750")));
        metrics.add(PerfMetrics.measure("GET /market/order-book", c, w, n,
            () -> api.orderBook("600000", 5)));
        metrics.add(PerfMetrics.measure("GET /market/rank", c, w, n, () -> api.rank(2, 2, 1, 10)));
        metrics.add(PerfMetrics.measure("GET /market/minute", c, w, n, () -> api.minute("600000", null)));
        metrics.add(PerfMetrics.measure("GET /market/trades", c, w, n,
            () -> api.marketTrades("600000", 50)));
        metrics.add(PerfMetrics.measure("GET /market/indexes", c, w, n, () -> api.indexes()));
        metrics.add(PerfMetrics.measure("GET /market/kline", c, w, n,
            () -> api.kline("600000", 1, 1, null, 200)));
        metrics.add(PerfMetrics.measure("GET /position", c, w, n, () -> api.positions(1L)));
        metrics.add(PerfMetrics.measure("GET /order", c, w, n,
            () -> api.orders(1L, null, null, 1, 10)));
        metrics.add(PerfMetrics.measure("GET /order/{id}", c, w, n, () -> api.orderDetail(1L)));
        metrics.add(PerfMetrics.measure("GET /trade", c, w, n, () -> api.trades(1L, null, 1, 10)));
        metrics.add(PerfMetrics.measure("GET /account", c, w, n, () -> api.accounts()));
        metrics.add(PerfMetrics.measure("GET /bank-card", c, w, n, () -> api.bankCards(1L)));
    }

    private void measureWrites(PerfMetrics metrics) {
        int w = WRITE_WARMUP;
        int n = WRITE_SAMPLES;

        metrics.add(PerfMetrics.measure("POST /user/code", WRITE_CONCURRENCY, w, n, () -> {
            requireSuccess(api.sendUserCode(PHONE));
        }));
        metrics.add(PerfMetrics.measure("POST /user/login/username", WRITE_CONCURRENCY, w, n, () -> {
            requireSuccess(api.loginUsername("admin", "123456"));
        }));
        metrics.add(PerfMetrics.measure("POST /user/login/phone", WRITE_CONCURRENCY, w, n, () -> {
            synchronized (stateLock) {
                String code = sendAndReadUserCode(PHONE);
                requireSuccess(api.loginPhone(PHONE, code));
            }
        }));
        metrics.add(PerfMetrics.measure("POST /user/register", WRITE_CONCURRENCY, w, n, () -> {
            int i = seq.incrementAndGet();
            String phone = "136" + String.format("%08d", i);
            String code = sendAndReadUserCode(phone);
            requireSuccess(api.register(ApiClient.body("phone", phone, "code", code,
                "username", "perf" + i, "password", "123456", "name", "压测",
                "idNumber", "110101199001011234")));
        }));
        metrics.add(PerfMetrics.measure("PUT /user/update", WRITE_CONCURRENCY, w, n, () -> {
            synchronized (stateLock) {
                String code = sendAndReadUserCode(PHONE);
                requireSuccess(api.updateUser(ApiClient.body("phone", PHONE, "code", code)));
            }
        }));
        metrics.add(PerfMetrics.measure("PUT /user/password/reset", WRITE_CONCURRENCY, w, n, () -> {
            synchronized (stateLock) {
                String code = sendAndReadUserCode(PHONE);
                requireSuccess(api.resetPassword(ApiClient.body(
                    "phone", PHONE, "code", code, "newPassword", "123456")));
            }
        }));
        metrics.add(PerfMetrics.measure("POST /account/open", WRITE_CONCURRENCY, w, n, () -> {
            requireSuccess(api.openAccount("123456"));
        }));
        metrics.add(PerfMetrics.measure("PUT /account/board/open", WRITE_CONCURRENCY, w, n, () -> {
            requireSuccess(api.openBoard(1L, List.of("创业板")));
        }));
        metrics.add(PerfMetrics.measure("PUT /account/password/update", WRITE_CONCURRENCY, w, n, () -> {
            synchronized (stateLock) {
                requireSuccess(api.updateTradePassword(1L, "123456", "654321"));
                requireSuccess(api.updateTradePassword(1L, "654321", "123456"));
            }
        }));
        metrics.add(PerfMetrics.measure("POST /account/password/verify", WRITE_CONCURRENCY, w, n, () -> {
            requireSuccess(api.verifyTradePassword(1L, "123456", 24));
        }));
        metrics.add(PerfMetrics.measure("PUT /account/deposit", WRITE_CONCURRENCY, w, n, () -> {
            requireSuccess(api.deposit(1L, new BigDecimal("1")));
        }));
        metrics.add(PerfMetrics.measure("PUT /account/withdraw", WRITE_CONCURRENCY, w, n, () -> {
            requireSuccess(api.verifyTradePassword(1L, "123456", 24));
            requireSuccess(api.withdraw(1L, new BigDecimal("1")));
        }));
        metrics.add(PerfMetrics.measure("GET /bank-card/code", WRITE_CONCURRENCY, w, n, () -> {
            requireSuccess(api.sendBankCardCode(PHONE));
        }));
        metrics.add(PerfMetrics.measure("POST /bank-card/verify", WRITE_CONCURRENCY, w, n, () -> {
            requireSuccess(api.verifyBankCard(CARD_NUMBER, PHONE));
        }));
        metrics.add(PerfMetrics.measure("POST /bank-card (bind+unbind)", WRITE_CONCURRENCY, w, n, () -> {
            synchronized (stateLock) {
                int i = seq.incrementAndGet();
                String card = luhnCard("6217" + String.format("%011d", i));
                String code = sendAndReadCardCode(PHONE);
                requireSuccess(api.bindBankCard(1L, card, "建设银行", PHONE, code));
                String unbindCode = sendAndReadCardCode(PHONE);
                requireSuccess(api.unbindBankCard(1L, card, PHONE, unbindCode));
            }
        }));
        metrics.add(PerfMetrics.measure("POST /order (create+cancel)", WRITE_CONCURRENCY, w, n, () -> {
            JsonNode created = api.createOrder(ApiClient.body("accountId", 1, "code", "600000",
                "type", 1, "price", new BigDecimal("9.50"), "quantity", 100,
                "orderKey", String.valueOf(600000L + seq.incrementAndGet())));
            requireSuccess(created);
            requireSuccess(api.cancelOrder(1L, created.path("data").path("id").asLong()));
        }));

        List<Long> pending = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            JsonNode created = api.createOrder(ApiClient.body("accountId", 1, "code", "600000",
                "type", 1, "price", new BigDecimal("9.50"), "quantity", 100,
                "orderKey", String.valueOf(700000L + i)));
            requireSuccess(created);
            pending.add(created.path("data").path("id").asLong());
        }
        AtomicInteger cancelIndex = new AtomicInteger();
        metrics.add(PerfMetrics.measure("PUT /order/cancel", WRITE_CONCURRENCY, 0, n, () -> {
            Long id = pending.get(cancelIndex.getAndIncrement());
            requireSuccess(api.cancelOrder(1L, id));
        }));
    }

    private void requireSuccess(JsonNode response) {
        if (response.path("code").asInt() != 1) {
            throw new IllegalStateException("接口返回失败：" + response);
        }
    }
}
