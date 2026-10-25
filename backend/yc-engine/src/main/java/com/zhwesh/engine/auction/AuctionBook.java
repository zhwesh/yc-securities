package com.zhwesh.engine.auction;

import com.zhwesh.VO.OrderBookVO;
import com.zhwesh.engine.model.EngineOrder;
import com.zhwesh.engine.model.OrderSide;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 单只股票集合竞价委托簿
 * 竞价阶段只收集委托，不连续撮合；收盘时按最大成交量原则产生单一成交价
 */
class AuctionBook {

    private final String code;
    private final List<EngineOrder> bids = new ArrayList<>();
    private final List<EngineOrder> asks = new ArrayList<>();

    AuctionBook(String code) {
        this.code = code;
    }

    void add(EngineOrder order) {
        if (order.getPrice() == null || order.getRemainingQuantity() <= 0) {
            return;
        }
        if (order.getSide() == OrderSide.BUY) {
            bids.add(order);
        } else {
            asks.add(order);
        }
    }

    boolean cancel(Long orderId) {
        return bids.removeIf(order -> order.getId().equals(orderId))
            || asks.removeIf(order -> order.getId().equals(orderId));
    }

    List<EngineOrder> orders() {
        List<EngineOrder> all = new ArrayList<>(bids.size() + asks.size());
        all.addAll(bids);
        all.addAll(asks);
        return all;
    }

    AuctionSnapshot snapshot(BigDecimal preClose) {
        PriceSelection selection = select(preClose);
        long matched = selection == null ? 0 : selection.matchedVolume;
        long unmatchedBuy = selection == null ? 0 : selection.eligibleBuyVolume - matched;
        long unmatchedSell = selection == null ? 0 : selection.eligibleSellVolume - matched;
        return AuctionSnapshot.builder()
            .code(code)
            .indicativePrice(selection == null ? null : selection.price)
            .matchedVolume(matched)
            .unmatchedBuyVolume(Math.max(unmatchedBuy, 0))
            .unmatchedSellVolume(Math.max(unmatchedSell, 0))
            .bids(levels(bids, true, 5))
            .asks(levels(asks, false, 5))
            .build();
    }

    AuctionResult close(BigDecimal preClose) {
        PriceSelection selection = select(preClose);
        List<AuctionMatch> matches = new ArrayList<>();
        if (selection != null && selection.matchedVolume > 0) {
            BigDecimal price = selection.price;
            List<EngineOrder> eligibleBuys = bids.stream()
                .filter(order -> order.getRemainingQuantity() > 0
                    && order.getPrice().compareTo(price) >= 0)
                .sorted(orderComparator())
                .toList();
            List<EngineOrder> eligibleSells = asks.stream()
                .filter(order -> order.getRemainingQuantity() > 0
                    && order.getPrice().compareTo(price) <= 0)
                .sorted(orderComparator())
                .toList();

            int buyIndex = 0;
            int sellIndex = 0;
            while (buyIndex < eligibleBuys.size() && sellIndex < eligibleSells.size()) {
                EngineOrder buyer = eligibleBuys.get(buyIndex);
                EngineOrder seller = eligibleSells.get(sellIndex);
                long quantity = Math.min(buyer.getRemainingQuantity(),
                    seller.getRemainingQuantity());
                buyer.setFilledQuantity(buyer.getFilledQuantity() + quantity);
                seller.setFilledQuantity(seller.getFilledQuantity() + quantity);
                matches.add(AuctionMatch.builder()
                    .buyer(buyer)
                    .seller(seller)
                    .price(price)
                    .quantity(quantity)
                    .build());
                if (buyer.isFilled()) {
                    buyIndex++;
                }
                if (seller.isFilled()) {
                    sellIndex++;
                }
            }
        }

        long matchedVolume = matches.stream().mapToLong(AuctionMatch::getQuantity).sum();
        List<EngineOrder> residual = orders().stream()
            .filter(order -> order.getRemainingQuantity() > 0)
            .toList();
        return AuctionResult.builder()
            .code(code)
            .price(matchedVolume > 0
                ? (selection == null ? null : selection.price) : null)
            .matchedVolume(matchedVolume)
            .matches(matches)
            .residualOrders(residual)
            .build();
    }

    /**
     * 最大成交量原则；同成交量时取最接近昨收的价格，仍相同取较高价
     */
    private PriceSelection select(BigDecimal preClose) {
        Set<BigDecimal> priceSet = new HashSet<>();
        for (EngineOrder order : bids) {
            if (order.getRemainingQuantity() > 0) {
                priceSet.add(order.getPrice());
            }
        }
        for (EngineOrder order : asks) {
            if (order.getRemainingQuantity() > 0) {
                priceSet.add(order.getPrice());
            }
        }
        if (priceSet.isEmpty()) {
            return null;
        }

        PriceSelection best = null;
        for (BigDecimal price : priceSet) {
            long buyVolume = 0L;
            for (EngineOrder order : bids) {
                if (order.getRemainingQuantity() > 0
                    && order.getPrice().compareTo(price) >= 0) {
                    buyVolume += order.getRemainingQuantity();
                }
            }
            long sellVolume = 0L;
            for (EngineOrder order : asks) {
                if (order.getRemainingQuantity() > 0
                    && order.getPrice().compareTo(price) <= 0) {
                    sellVolume += order.getRemainingQuantity();
                }
            }
            PriceSelection candidate = new PriceSelection(price, buyVolume, sellVolume,
                Math.min(buyVolume, sellVolume));
            if (best == null || better(candidate, best, preClose)) {
                best = candidate;
            }
        }
        return best;
    }

    private boolean better(PriceSelection candidate, PriceSelection current,
        BigDecimal preClose) {
        if (candidate.matchedVolume != current.matchedVolume) {
            return candidate.matchedVolume > current.matchedVolume;
        }
        if (preClose != null) {
            BigDecimal candidateDistance = candidate.price.subtract(preClose).abs();
            BigDecimal currentDistance = current.price.subtract(preClose).abs();
            int compare = candidateDistance.compareTo(currentDistance);
            if (compare != 0) {
                return compare < 0;
            }
        }
        return candidate.price.compareTo(current.price) > 0;
    }

    private List<OrderBookVO.OrderBookItemVO> levels(List<EngineOrder> orders,
        boolean descending, int depth) {
        Comparator<BigDecimal> comparator = descending
            ? Comparator.reverseOrder() : Comparator.naturalOrder();
        TreeMap<BigDecimal, Long> volumes = new TreeMap<>(comparator);
        for (EngineOrder order : orders) {
            if (order.getRemainingQuantity() > 0) {
                volumes.merge(order.getPrice(), order.getRemainingQuantity(), Long::sum);
            }
        }
        List<OrderBookVO.OrderBookItemVO> result = new ArrayList<>();
        for (Map.Entry<BigDecimal, Long> entry : volumes.entrySet()) {
            if (result.size() >= depth) {
                break;
            }
            result.add(OrderBookVO.OrderBookItemVO.builder()
                .price(entry.getKey())
                .volume(BigDecimal.valueOf(entry.getValue()).movePointLeft(2))
                .build());
        }
        return result;
    }

    private Comparator<EngineOrder> orderComparator() {
        return Comparator.comparing(EngineOrder::getOrderTime)
            .thenComparing(EngineOrder::getId);
    }

    private record PriceSelection(BigDecimal price, long eligibleBuyVolume,
                                  long eligibleSellVolume, long matchedVolume) {
    }
}
