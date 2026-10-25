package com.zhwesh.engine.auction;

import com.zhwesh.engine.model.EngineOrder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 集合竞价引擎：开盘/收盘竞价委托收集、虚拟撮合价、收盘统一撮合
 */
@Component
public class CallAuctionEngine {

    private final Map<String, AuctionBook> openingBooks = new ConcurrentHashMap<>();
    private final Map<String, AuctionBook> closingBooks = new ConcurrentHashMap<>();

    /**
     * 9:25-9:30 及开盘竞价未成交委托，9:30 释放到连续交易
     */
    private final Map<String, List<EngineOrder>> pendingContinuous = new ConcurrentHashMap<>();

    private final Map<String, AuctionSnapshot> lastOpeningSnapshots = new ConcurrentHashMap<>();
    private final Map<String, AuctionSnapshot> lastClosingSnapshots = new ConcurrentHashMap<>();

    /**
     * 开盘集合竞价收单
     */
    public synchronized void acceptOpening(EngineOrder order) {
        if (order.getPrice() == null) {
            return;
        }
        openingBooks.computeIfAbsent(order.getCode(), AuctionBook::new).add(order);
    }

    /**
     * 收盘集合竞价收单
     */
    public synchronized void acceptClosing(EngineOrder order) {
        if (order.getPrice() == null) {
            return;
        }
        closingBooks.computeIfAbsent(order.getCode(), AuctionBook::new).add(order);
    }

    /**
     * 9:25-9:30 委托，暂存到 9:30 释放
     */
    public synchronized void acceptPending(EngineOrder order) {
        pendingContinuous.computeIfAbsent(order.getCode(), key -> new ArrayList<>()).add(order);
    }

    /**
     * 14:57 将连续交易未成交委托转入收盘集合竞价
     */
    public synchronized void addClosingOrders(List<EngineOrder> orders) {
        for (EngineOrder order : orders) {
            acceptClosing(order);
        }
    }

    /**
     * 撤销集合竞价委托
     * @return 是否存在于竞价簿
     */
    public synchronized boolean cancel(String code, Long orderId) {
        AuctionBook opening = openingBooks.get(code);
        if (opening != null && opening.cancel(orderId)) {
            return true;
        }
        AuctionBook closing = closingBooks.get(code);
        return closing != null && closing.cancel(orderId);
    }

    /**
     * 开盘竞价快照（没有活动竞价时返回最近一次）
     */
    public synchronized AuctionSnapshot snapshotOpening(String code, BigDecimal preClose) {
        AuctionBook book = openingBooks.get(code);
        if (book != null) {
            return book.snapshot(preClose);
        }
        return lastOpeningSnapshots.getOrDefault(code, emptySnapshot(code));
    }

    /**
     * 收盘竞价快照（没有活动竞价时返回最近一次）
     */
    public synchronized AuctionSnapshot snapshotClosing(String code, BigDecimal preClose) {
        AuctionBook book = closingBooks.get(code);
        if (book != null) {
            return book.snapshot(preClose);
        }
        return lastClosingSnapshots.getOrDefault(code, emptySnapshot(code));
    }

    /**
     * 关闭开盘集合竞价
     */
    public synchronized AuctionResult closeOpening(String code, BigDecimal preClose) {
        AuctionBook book = openingBooks.remove(code);
        if (book == null) {
            return null;
        }
        lastOpeningSnapshots.put(code, book.snapshot(preClose));
        return book.close(preClose);
    }

    /**
     * 关闭收盘集合竞价
     */
    public synchronized AuctionResult closeClosing(String code, BigDecimal preClose) {
        AuctionBook book = closingBooks.remove(code);
        if (book == null) {
            return null;
        }
        lastClosingSnapshots.put(code, book.snapshot(preClose));
        return book.close(preClose);
    }

    /**
     * 取出并清空 9:25-9:30 及开盘竞价未成交委托
     */
    public synchronized List<EngineOrder> drainPending(String code) {
        List<EngineOrder> orders = pendingContinuous.remove(code);
        return orders == null ? List.of() : orders;
    }

    public Set<String> openingCodes() {
        return new HashSet<>(openingBooks.keySet());
    }

    public Set<String> closingCodes() {
        return new HashSet<>(closingBooks.keySet());
    }

    public Set<String> pendingCodes() {
        return new HashSet<>(pendingContinuous.keySet());
    }

    /**
     * 跨日清理
     */
    public synchronized void clearAll() {
        openingBooks.clear();
        closingBooks.clear();
        pendingContinuous.clear();
        lastOpeningSnapshots.clear();
        lastClosingSnapshots.clear();
    }

    private AuctionSnapshot emptySnapshot(String code) {
        return AuctionSnapshot.builder()
            .code(code)
            .matchedVolume(0L)
            .unmatchedBuyVolume(0L)
            .unmatchedSellVolume(0L)
            .bids(List.of())
            .asks(List.of())
            .build();
    }
}
