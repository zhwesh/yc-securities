package com.zhwesh.apitest.support;

import com.zhwesh.engine.match.MatchEngine;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Base64;

/**
 * 接口测试基类：独立测试库 + Redis db2，方法级重建数据
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"dev", "test"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class ApiTestBase {

    /**
     * 123456 的 BCrypt
     */
    public static final String PASSWORD_HASH =
        "$2a$10$3jcJWC8eZ231Wc5tlmYy5uCLMo8wOUlCN2./bEZzXUtoIo8gIgEHO";

    /**
     * 测试手机号
     */
    public static final String PHONE = "13800138000";

    /**
     * 测试银行卡（Luhn 通过）
     */
    public static final String CARD_NUMBER = "6228480402564890018";

    /**
     * 1x1 PNG（头像上传用）
     */
    public static final byte[] AVATAR_PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @LocalServerPort
    protected int port;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @Autowired
    protected MatchEngine matchEngine;

    protected ApiClient api;

    @BeforeEach
    void setUpBase() {
        resetDatabase();
        flushRedis();
        matchEngine.clearAllOrders();
        api = new ApiClient(port);
        api.loginUsername("admin", "123456");
    }

    /**
     * 重建测试数据（admin 账户 100 万 + 银行卡 + 000001 持仓 + 20 笔委托/成交样本）
     */
    protected void resetDatabase() {
        String[] tables = {"orders", "sold_orders", "`position`", "account", "bank_card",
            "watchlist", "minute_quote"};
        for (String table : tables) {
            jdbcTemplate.execute("TRUNCATE TABLE " + table);
        }
        jdbcTemplate.update("DELETE FROM daily_quote WHERE date >= CURDATE()");
        jdbcTemplate.execute("TRUNCATE TABLE user");

        jdbcTemplate.update("INSERT INTO user(password, name, id_number, username, phone, image, create_time) " +
                "VALUES (?, ?, ?, ?, ?, NULL, NOW())",
            PASSWORD_HASH, "admin", "110101199001011234", "admin", PHONE);
        jdbcTemplate.update("INSERT INTO account(password, user_id, available_assets, frozen_assets, " +
                "boards, create_time, status) VALUES (?, 1, ?, 0, ?, NOW(), 1)",
            PASSWORD_HASH, new BigDecimal("1000000"), "主板,创业板,科创板,北交所");
        jdbcTemplate.update("INSERT INTO bank_card(card_number, bank_name, account_id, phone, create_time) " +
                "VALUES (?, ?, 1, ?, NOW())",
            CARD_NUMBER, "农业银行", PHONE);
        jdbcTemplate.update("INSERT INTO `position`(account_id, stock_code, stock_name, cost_price, " +
                "quantity, available_quantity, today_buy_quantity) " +
                "VALUES (1, '000001', '平安银行', 11.57, 10000, 10000, 0)");

        String[] codes = {"600000", "000001", "300750"};
        BigDecimal[] prices = {new BigDecimal("9.50"), new BigDecimal("11.50"), new BigDecimal("289.00")};
        for (int i = 0; i < 20; i++) {
            int status = i / 5 + 1;
            int quantity = i % 2 == 0 ? 100 : -100;
            int sold = switch (status) {
                case 2 -> 50;
                case 3 -> 100;
                default -> 0;
            };
            BigDecimal price = prices[i % 3];
            jdbcTemplate.update("INSERT INTO orders(order_key, account_id, stock_code, price, freeze_price, " +
                    "quantity, sold_quantity, type, status, create_time) " +
                    "VALUES (?, 1, ?, ?, ?, ?, ?, 1, ?, NOW() - INTERVAL " + i + " MINUTE)",
                900001 + i, codes[i % 3], price, price, quantity, sold, status);
        }
        for (int i = 0; i < 20; i++) {
            int quantity = i % 2 == 0 ? 100 : -100;
            String code = i < 10 ? "000001" : "600000";
            jdbcTemplate.update("INSERT INTO sold_orders(order_id, account_id, stock_code, price, quantity, create_time) " +
                    "VALUES (?, 1, ?, ?, ?, NOW() - INTERVAL " + i + " MINUTE)",
                i + 1, code, new BigDecimal("11.50"), quantity);
        }
    }

    /**
     * 清空 Redis db2
     */
    protected void flushRedis() {
        redisTemplate.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    /**
     * 发送验证码并读取（短信为模拟实现，验证码直接存 Redis）
     */
    protected String sendAndReadUserCode(String phone) {
        api.sendUserCode(phone);
        return redisTemplate.opsForValue().get("sms:code:" + phone);
    }

    /**
     * 发送银行卡验证码并读取
     */
    protected String sendAndReadCardCode(String phone) {
        api.sendBankCardCode(phone);
        return redisTemplate.opsForValue().get("sms:code:" + phone);
    }

    /**
     * 生成 Luhn 校验通过的银行卡号
     */
    protected static String luhnCard(String prefix15) {
        int sum = 0;
        boolean doubleDigit = true;
        for (int i = prefix15.length() - 1; i >= 0; i--) {
            int digit = prefix15.charAt(i) - '0';
            if (doubleDigit) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleDigit = !doubleDigit;
        }
        return prefix15 + (10 - sum % 10) % 10;
    }
}
