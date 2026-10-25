package com.zhwesh.engine.match;

import com.zhwesh.VO.OrderBookVO;
import com.zhwesh.engine.model.EngineOrder;
import com.zhwesh.engine.model.EngineOrderType;
import com.zhwesh.engine.model.EngineTrade;
import com.zhwesh.engine.model.OrderSide;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 单只股票的订单簿
 * 买盘价格降序（reverseOrder）、卖盘价格升序；同价按时间先进先出。
 * 非线程安全：由撮合入口（MatchEngine）加锁串行访问。
 */
public class OrderBook {

    private final String code;

    /**
     * 买盘：价格降序（最高买价优先）
     */
    private final TreeMap<BigDecimal, Deque<EngineOrder>> bids =
        new TreeMap<>(Comparator.reverseOrder());

    /**
     * 卖盘：价格升序（最低卖价优先）
     */
    private final TreeMap<BigDecimal, Deque<EngineOrder>> asks =
        new TreeMap<>();

    /**
     * 订单索引：orderId->订单
     */
    private final Map<Long, EngineOrder> orderIndex = new HashMap<>();

    public OrderBook(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /**
     * 挂单并撮合
     * 限价单未成交部分挂在盘口；市价单未成交部分直接撤销（A 股规则）
     * @param order 委托单（限价单价格规范为2位小数）
     * @return 成交列表
     */
    public List<EngineTrade> place(EngineOrder order) {
        List<EngineTrade> trades = new ArrayList<>();
        if (order.getSide() == OrderSide.BUY) {
            match(order, trades, asks, true);
            if (order.getRemainingQuantity() > 0 && order.getType() == EngineOrderType.LIMIT) {
                addToBook(bids, order);
            }
        } else {
            match(order, trades, bids, false);
            if (order.getRemainingQuantity() > 0 && order.getType() == EngineOrderType.LIMIT) {
                addToBook(asks, order);
            }
        }
        return trades;
    }

    /**
     * 尝试撮合主动单与对手盘
     * @param taker 主动单
     * @param trades 成交列表
     * @param opposite 对手盘（买单对卖盘，卖单对买盘）
     * @param isBuyTaker 主动方是否为买入
     */
    private void match(EngineOrder taker, List<EngineTrade> trades,
                       TreeMap<BigDecimal, Deque<EngineOrder>> opposite, boolean isBuyTaker) {
        while (taker.getRemainingQuantity() > 0 && !opposite.isEmpty()) {
            Map.Entry<BigDecimal, Deque<EngineOrder>> bestLevel = opposite.firstEntry();
            BigDecimal makerPrice = bestLevel.getKey();

            // 限价单价格不满足则停止；市价单一直成交直到数量达标或盘口空
            if (taker.getType() == EngineOrderType.LIMIT) {
                if (isBuyTaker && makerPrice.compareTo(taker.getPrice()) > 0) {
                    break;
                }
                if (!isBuyTaker && makerPrice.compareTo(taker.getPrice()) < 0) {
                    break;
                }
            }

            Deque<EngineOrder> queue = bestLevel.getValue();
            while (taker.getRemainingQuantity() > 0 && !queue.isEmpty()) {
                EngineOrder maker = queue.peekFirst();
                long tradeQuantity = Math.min(taker.getRemainingQuantity(), maker.getRemainingQuantity());

                taker.setFilledQuantity(taker.getFilledQuantity() + tradeQuantity);
                maker.setFilledQuantity(maker.getFilledQuantity() + tradeQuantity);

                trades.add(EngineTrade.builder()
                    .takerOrderId(taker.getId())
                    .makerOrderId(maker.getId())
                    .takerAccountId(taker.getAccountId())
                    .makerAccountId(maker.getAccountId())
                    .code(code)
                    .takerSide(taker.getSide())
                    .price(makerPrice)
                    .quantity(tradeQuantity)
                    .tradeTime(LocalDateTime.now())
                    .build());

                if (maker.isFilled()) {
                    queue.pollFirst();
                    orderIndex.remove(maker.getId());
                }
            }
            if (queue.isEmpty()) {
                opposite.pollFirstEntry();
            }
        }
    }

    /**
     * 挂到本方盘口
     */
    private void addToBook(TreeMap<BigDecimal, Deque<EngineOrder>> book, EngineOrder order) {
        book.computeIfAbsent(order.getPrice(), k -> new ArrayDeque<>()).addLast(order);
        orderIndex.put(order.getId(), order);
    }

    /**
     * 撤单
     * @param orderId 委托单id
     * @return 是否撤销成功（订单不在簿中，如已全部成交或已撤销，返回 false）
     */
    public boolean cancel(Long orderId) {
        EngineOrder order = orderIndex.remove(orderId);
        if (order == null) {
            return false;
        }
        TreeMap<BigDecimal, Deque<EngineOrder>> book = order.getSide() == OrderSide.BUY ? bids : asks;
        Deque<EngineOrder> queue = book.get(order.getPrice());
        if (queue != null) {
            queue.remove(order);
            if (queue.isEmpty()) {
                book.remove(order.getPrice());
            }
        }
        return true;
    }

    /**
     * 取出并清空全部活动委托（收盘集合竞价切换用）
     */
    public List<EngineOrder> drain() {
        List<EngineOrder> orders = new ArrayList<>(orderIndex.values());
        bids.clear();
        asks.clear();
        orderIndex.clear();
        return orders;
    }

    /**
     * 获取盘口快照
     * @param depth 档位
     */
    public OrderBookVO snapshot(int depth) {
        return OrderBookVO.builder()
            .code(code)
            .depth(depth)
            .bids(levels(bids, depth))
            .asks(levels(asks, depth))
            .build();
    }

    /**
     * 取前depth个档位并汇总每档数量（成交量单位：股，对外展示折算为手）
     */
    private List<OrderBookVO.OrderBookItemVO> levels(
        TreeMap<BigDecimal, Deque<EngineOrder>> book, int depth) {
        List<OrderBookVO.OrderBookItemVO> levels = new ArrayList<>();
        for (Map.Entry<BigDecimal, Deque<EngineOrder>> entry : book.entrySet()) {
            if (levels.size() >= depth) {
                break;
            }
            long shares = 0;
            for (EngineOrder order : entry.getValue()) {
                shares += order.getRemainingQuantity();
            }
            levels.add(OrderBookVO.OrderBookItemVO.builder()
                .price(entry.getKey())
                .volume(BigDecimal.valueOf(shares).movePointLeft(2))
                .build());
        }
        return levels;
    }

    /**
     * 买一价
     */
    public BigDecimal bestBid() {
        return bids.isEmpty() ? null : bids.firstKey();
    }

    /**
     * 卖一价
     */
    public BigDecimal bestAsk() {
        return asks.isEmpty() ? null : asks.firstKey();
    }
}
