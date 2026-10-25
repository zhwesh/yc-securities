package com.zhwesh.util;

import com.zhwesh.constant.TradingPhase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/**
 * 交易时间判断工具类
 * trading.always-on=true：测试/演示模式，24 小时连续交易，不启用集合竞价；
 * false：按 A 股交易时段运行，支持开盘集合竞价 09:15-09:25、收盘集合竞价 14:57-15:00
 */
@Component
public class TradingTimeUtil {

    /**
     * 上午交易开始时间
     */
    private static final LocalTime MORNING_OPEN = LocalTime.of(9, 30);

    /**
     * 上午交易结束时间
     */
    private static final LocalTime MORNING_CLOSE = LocalTime.of(11, 30);

    /**
     * 下午交易开始时间
     */
    private static final LocalTime AFTERNOON_OPEN = LocalTime.of(13, 0);

    /**
     * 下午连续交易结束时间
     */
    private static final LocalTime AFTERNOON_CONTINUOUS_CLOSE = LocalTime.of(14, 57);

    /**
     * 收盘集合竞价结束时间
     */
    private static final LocalTime AFTERNOON_CLOSE = LocalTime.of(15, 0);

    @Value("${trading.always-on:true}")
    private boolean alwaysOn;

    @Value("${trading.call-auction-enabled:true}")
    private boolean callAuctionEnabled;

    /**
     * 测试用固定时间（ISO-8601），为空时使用系统时间
     */
    @Value("${trading.test-now:}")
    private String testNow;

    /**
     * 当前时间（测试环境可通过 trading.test-now 固定）
     */
    public LocalDateTime now() {
        if (testNow != null && !testNow.isBlank()) {
            return LocalDateTime.parse(testNow.trim());
        }
        return LocalDateTime.now();
    }

    /**
     * 当前交易时段
     */
    public TradingPhase getPhase() {
        return getPhase(now());
    }

    /**
     * 指定时间的交易时段
     */
    public TradingPhase getPhase(LocalDateTime dateTime) {
        if (alwaysOn) {
            return TradingPhase.CONTINUOUS;
        }
        DayOfWeek dayOfWeek = dateTime.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return TradingPhase.CLOSED;
        }

        LocalTime time = dateTime.toLocalTime();
        if (callAuctionEnabled) {
            if (!time.isBefore(LocalTime.of(9, 15)) && time.isBefore(LocalTime.of(9, 20))) {
                return TradingPhase.OPEN_AUCTION_CANCELABLE;
            }
            if (!time.isBefore(LocalTime.of(9, 20)) && time.isBefore(LocalTime.of(9, 25))) {
                return TradingPhase.OPEN_AUCTION_LOCKED;
            }
            if (!time.isBefore(LocalTime.of(9, 25)) && time.isBefore(MORNING_OPEN)) {
                return TradingPhase.OPEN_AUCTION_FREEZE;
            }
        }

        boolean morning = !time.isBefore(MORNING_OPEN) && !time.isAfter(MORNING_CLOSE);
        boolean afternoon = !time.isBefore(AFTERNOON_OPEN)
            && time.isBefore(AFTERNOON_CONTINUOUS_CLOSE);
        if (morning || afternoon) {
            return TradingPhase.CONTINUOUS;
        }
        if (callAuctionEnabled
            && !time.isBefore(AFTERNOON_CONTINUOUS_CLOSE) && time.isBefore(AFTERNOON_CLOSE)) {
            return TradingPhase.CLOSE_AUCTION;
        }
        return TradingPhase.CLOSED;
    }

    /**
     * 当前是否处于连续交易时段（快照/做市机器人使用）
     */
    public boolean isTradingTime() {
        if (alwaysOn) {
            return true;
        }

        LocalDateTime now = now();
        DayOfWeek dayOfWeek = now.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return false;
        }

        LocalTime time = now.toLocalTime();
        boolean morning = !time.isBefore(MORNING_OPEN) && !time.isAfter(MORNING_CLOSE);
        boolean afternoon = !time.isBefore(AFTERNOON_OPEN) && !time.isAfter(AFTERNOON_CLOSE);
        return morning || afternoon;
    }

    public boolean isAlwaysOn() {
        return alwaysOn;
    }

    public boolean isCallAuctionEnabled() {
        return callAuctionEnabled;
    }

    /**
     * 今日已交易分钟数（按 A 股时段：9:30 前为 0、上午按已过分钟、午休为 120、下午累加、15:00 后为 240）
     */
    public int getElapsedTradingMinutes() {
        LocalTime time = now().toLocalTime();
        if (time.isBefore(MORNING_OPEN)) {
            return 0;
        }
        if (time.isBefore(MORNING_CLOSE)) {
            return (int) ChronoUnit.MINUTES.between(MORNING_OPEN, time);
        }
        if (time.isBefore(AFTERNOON_OPEN)) {
            return 120;
        }
        if (time.isBefore(AFTERNOON_CLOSE)) {
            return 120 + (int) ChronoUnit.MINUTES.between(AFTERNOON_OPEN, time);
        }
        return 240;
    }
}
