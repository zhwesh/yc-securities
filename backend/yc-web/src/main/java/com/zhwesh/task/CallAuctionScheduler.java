package com.zhwesh.task;

import com.zhwesh.service.CallAuctionService;
import com.zhwesh.util.TradingTimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 集合竞价时段调度：
 * 09:25 开盘竞价撮合；09:30 释放未成交委托；14:57 连续委托转入收盘竞价；15:00 收盘竞价撮合
 */
@Slf4j
@Component
public class CallAuctionScheduler {

    private final CallAuctionService callAuctionService;
    private final TradingTimeUtil tradingTimeUtil;

    private LocalDate today = LocalDate.now();
    private boolean openingClosed;
    private boolean openingReleased;
    private boolean closingPrepared;
    private boolean closingClosed;

    @Autowired
    public CallAuctionScheduler(CallAuctionService callAuctionService,
        TradingTimeUtil tradingTimeUtil) {
        this.callAuctionService = callAuctionService;
        this.tradingTimeUtil = tradingTimeUtil;
    }

    @Scheduled(fixedDelay = 1000)
    public void tick() {
        if (tradingTimeUtil.isAlwaysOn() || !tradingTimeUtil.isCallAuctionEnabled()) {
            return;
        }
        LocalDateTime now = tradingTimeUtil.now();
        LocalDate date = now.toLocalDate();
        if (!date.equals(today)) {
            today = date;
            openingClosed = false;
            openingReleased = false;
            closingPrepared = false;
            closingClosed = false;
            callAuctionService.clearDay();
        }

        LocalTime time = now.toLocalTime();
        try {
            if (!openingClosed && !time.isBefore(LocalTime.of(9, 25))
                && time.isBefore(LocalTime.of(15, 0))) {
                callAuctionService.closeOpeningAuction(now);
                openingClosed = true;
            }
            if (!openingReleased && !time.isBefore(LocalTime.of(9, 30))
                && time.isBefore(LocalTime.of(15, 0))) {
                callAuctionService.releaseOpeningOrders();
                openingReleased = true;
            }
            if (!closingPrepared && !time.isBefore(LocalTime.of(14, 57))
                && time.isBefore(LocalTime.of(15, 0))) {
                callAuctionService.prepareClosingAuction();
                closingPrepared = true;
            }
            if (!closingClosed && !time.isBefore(LocalTime.of(15, 0))) {
                callAuctionService.closeClosingAuction(now);
                closingClosed = true;
            }
        } catch (Exception e) {
            log.error("集合竞价调度异常", e);
        }
    }
}
