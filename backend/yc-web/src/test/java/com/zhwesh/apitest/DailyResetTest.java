package com.zhwesh.apitest;

import com.zhwesh.apitest.support.ApiTestBase;
import com.zhwesh.mapper.PositionMapper;
import com.zhwesh.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 每日可卖数量重置测试
 */
class DailyResetTest extends ApiTestBase {

    @Autowired
    private PositionMapper positionMapper;

    @Autowired
    private OrderService orderService;

    /**
     * 重置无当日买入的昨日持仓
     */
    @Test
    void resetStaleDailyAvailable() {
        jdbcTemplate.execute("TRUNCATE TABLE sold_orders");
        jdbcTemplate.execute("TRUNCATE TABLE orders");
        jdbcTemplate.update("UPDATE `position` SET available_quantity = 0, today_buy_quantity = 10000 " +
            "WHERE account_id = 1 AND stock_code = '000001'");

        positionMapper.resetDailyAvailable(LocalDate.now());

        assertThat(jdbcTemplate.queryForObject(
            "SELECT available_quantity FROM `position` WHERE account_id = 1 AND stock_code = '000001'",
            Long.class)).isEqualTo(10000L);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT today_buy_quantity FROM `position` WHERE account_id = 1 AND stock_code = '000001'",
            Long.class)).isEqualTo(0L);
    }

    /**
     * 保留当日买入持仓的锁定数量
     */
    @Test
    void keepTodayBuyLocked() {
        jdbcTemplate.execute("TRUNCATE TABLE sold_orders");
        jdbcTemplate.execute("TRUNCATE TABLE orders");
        jdbcTemplate.update("UPDATE `position` SET available_quantity = 0, today_buy_quantity = 10000 " +
            "WHERE account_id = 1 AND stock_code = '000001'");
        jdbcTemplate.update("INSERT INTO sold_orders(order_id, account_id, stock_code, price, " +
                "quantity, taker_side, create_time) VALUES (1, 1, '000001', 11.50, 10000, 0, NOW())");

        positionMapper.resetDailyAvailable(LocalDate.now());

        assertThat(jdbcTemplate.queryForObject(
            "SELECT available_quantity FROM `position` WHERE account_id = 1 AND stock_code = '000001'",
            Long.class)).isEqualTo(0L);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT today_buy_quantity FROM `position` WHERE account_id = 1 AND stock_code = '000001'",
            Long.class)).isEqualTo(10000L);
    }

    /**
     * 保留当日卖单冻结的持仓数量
     */
    @Test
    void keepTodayPendingSellLocked() {
        jdbcTemplate.execute("TRUNCATE TABLE sold_orders");
        jdbcTemplate.execute("TRUNCATE TABLE orders");
        jdbcTemplate.update("UPDATE `position` SET available_quantity = 5000, today_buy_quantity = 10000 " +
            "WHERE account_id = 1 AND stock_code = '000001'");
        jdbcTemplate.update("INSERT INTO orders(order_key, account_id, stock_code, price, quantity, " +
                "sold_quantity, type, status, create_time) " +
                "VALUES (300001, 1, '000001', 11.50, -5000, 0, 1, 1, NOW())");

        positionMapper.resetDailyAvailable(LocalDate.now());

        assertThat(jdbcTemplate.queryForObject(
            "SELECT available_quantity FROM `position` WHERE account_id = 1 AND stock_code = '000001'",
            Long.class)).isEqualTo(5000L);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT today_buy_quantity FROM `position` WHERE account_id = 1 AND stock_code = '000001'",
            Long.class)).isEqualTo(10000L);
    }

    /**
     * 撤销启动前的未成交委托
     */
    @Test
    void cancelPendingOrdersBeforeToday() {
        jdbcTemplate.execute("TRUNCATE TABLE sold_orders");
        jdbcTemplate.execute("TRUNCATE TABLE orders");
        jdbcTemplate.update("UPDATE `position` SET available_quantity = 5000, today_buy_quantity = 0 " +
            "WHERE account_id = 1 AND stock_code = '000001'");
        jdbcTemplate.update("INSERT INTO orders(order_key, account_id, stock_code, price, quantity, " +
                "sold_quantity, type, status, create_time) " +
                "VALUES (300002, 1, '000001', 11.50, -5000, 0, 1, 1, NOW() - INTERVAL 1 DAY)");

        orderService.cancelPendingOrdersBefore(LocalDate.now());

        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM orders WHERE order_key = 300002", Integer.class)).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT available_quantity FROM `position` WHERE account_id = 1 AND stock_code = '000001'",
            Long.class)).isEqualTo(10000L);
    }
}
