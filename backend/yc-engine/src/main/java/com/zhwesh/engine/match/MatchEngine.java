package com.zhwesh.engine.match;

import com.zhwesh.VO.OrderBookVO;
import com.zhwesh.engine.model.EngineOrder;
import com.zhwesh.engine.model.EngineOrderType;
import com.zhwesh.engine.model.EngineTrade;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 撮合引擎
 * 管理所有股票的订单簿，对yc-web模块暴露挂单/撤单/盘口查询接口。
 * 当前用订单簿级锁保证串行（后续如需高吞吐可改为单线程事件循环）。
 */
@Component
public class MatchEngine {

    /**
     * 单只股票保留的最大成交笔数
     */
    private static final int MAX_TRADES_PER_STOCK = 200;

    /**
     * code->订单簿
     */
    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();

    /**
     * code->最近成交流（按时间升序）
     */
    private final Map<String, Deque<EngineTrade>> recentTrades = new ConcurrentHashMap<>();

    /**
     * 挂单并撮合
     * @param order 委托单
     * @return 成交列表（主动单的已成交数量、状态由撮合更新）
     */
    public List<EngineTrade> placeOrder(EngineOrder order) {
        // 基础参数校验（资金、权限、涨跌停等业务校验由调用方负责）
        if (order.getQuantity() <= 0) {
            throw new IllegalArgumentException("委托数量必须大于0");
        }
        if (order.getType() == EngineOrderType.LIMIT) {
            if (order.getPrice() == null || order.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("限价单价格必须大于0");
            }
            // 价格规范为2位小数（A股最小变动价位0.01）
            order.setPrice(order.getPrice().setScale(2, RoundingMode.HALF_UP));
        }

        OrderBook book = orderBooks.computeIfAbsent(order.getCode(), OrderBook::new);
        List<EngineTrade> trades;
        synchronized (book) {
            trades = book.place(order);
        }
        recordTrades(trades);
        return trades;
    }

    /**
     * 撤单
     * @param code 股票代码
     * @param orderId 委托单id
     * @return 是否撤销成功
     */
    public boolean cancelOrder(String code, Long orderId) {
        OrderBook book = orderBooks.get(code);
        if (book == null) {
            return false;
        }
        synchronized (book) {
            return book.cancel(orderId);
        }
    }

    /**
     * 获取盘口快照
     * @param code 股票代码
     * @param depth 档位数
     * @return 盘口快照（无订单簿时返回空盘口）
     */
    public OrderBookVO getSnapshot(String code, int depth) {
        OrderBook book = orderBooks.get(code);
        if (book == null) {
            return OrderBookVO.builder()
                .code(code)
                .depth(depth)
                .bids(List.of())
                .asks(List.of())
                .build();
        }
        synchronized (book) {
            return book.snapshot(depth);
        }
    }

    /**
     * 买一价
     */
    public BigDecimal bestBid(String code) {
        OrderBook book = orderBooks.get(code);
        if (book == null) {
            return null;
        }
        synchronized (book) {
            return book.bestBid();
        }
    }

    /**
     * 卖一价
     */
    public BigDecimal bestAsk(String code) {
        OrderBook book = orderBooks.get(code);
        if (book == null) {
            return null;
        }
        synchronized (book) {
            return book.bestAsk();
        }
    }

    /**
     * 取出并清空全部连续交易活动委托（14:57 转入收盘集合竞价）
     */
    public List<EngineOrder> drainAllOrders() {
        List<EngineOrder> orders = new ArrayList<>();
        for (OrderBook book : orderBooks.values()) {
            synchronized (book) {
                orders.addAll(book.drain());
            }
        }
        return orders;
    }

    /**
     * 记录一笔外部成交（集合竞价成交等）
     */
    public void recordAuctionTrade(EngineTrade trade) {
        recordTrades(List.of(trade));
    }

    /**
     * 记录最近成交（超过上限时丢弃最旧的）
     */
    private void recordTrades(List<EngineTrade> trades) {
        for (EngineTrade trade : trades) {
            Deque<EngineTrade> deque = recentTrades.computeIfAbsent(
                trade.getCode(), key -> new ConcurrentLinkedDeque<>());
            deque.addLast(trade);
            while (deque.size() > MAX_TRADES_PER_STOCK) {
                deque.pollFirst();
            }
        }
    }

    /**
     * 查询最近成交（最新在前）
     * @param code 股票代码
     * @param limit 笔数
     * @return 成交列表
     */
    public List<EngineTrade> getTrades(String code, int limit) {
        Deque<EngineTrade> deque = recentTrades.get(code);
        if (deque == null || deque.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<EngineTrade> trades = new ArrayList<>(Math.min(limit, deque.size()));
        Iterator<EngineTrade> iterator = deque.descendingIterator();
        while (iterator.hasNext() && trades.size() < limit) {
            trades.add(iterator.next());
        }
        return trades;
    }

    /**
     * 清空成交流（跨日调用）
     */
    public void clearTrades() {
        recentTrades.clear();
    }

    /**
     * 清空全部订单簿与成交流（测试/运维用）
     */
    public void clearAllOrders() {
        orderBooks.clear();
        recentTrades.clear();
    }
}
