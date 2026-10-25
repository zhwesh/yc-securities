package com.zhwesh.service.impl;

import com.zhwesh.VO.CallAuctionVO;
import com.zhwesh.VO.StockQuoteVO;
import com.zhwesh.constant.TradingPhase;
import com.zhwesh.engine.auction.AuctionResult;
import com.zhwesh.engine.auction.AuctionSnapshot;
import com.zhwesh.engine.auction.CallAuctionEngine;
import com.zhwesh.engine.match.MatchEngine;
import com.zhwesh.engine.model.EngineOrder;
import com.zhwesh.engine.market.MarketEngine;
import com.zhwesh.entity.Stock;
import com.zhwesh.exception.BusinessException;
import com.zhwesh.mapper.StockMapper;
import com.zhwesh.service.CallAuctionService;
import com.zhwesh.service.OrderService;
import com.zhwesh.util.TradingTimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
public class CallAuctionServiceImpl implements CallAuctionService {

    private final CallAuctionEngine callAuctionEngine;
    private final MarketEngine marketEngine;
    private final MatchEngine matchEngine;
    private final StockMapper stockMapper;
    private final OrderService orderService;
    private final TradingTimeUtil tradingTimeUtil;

    @Autowired
    public CallAuctionServiceImpl(CallAuctionEngine callAuctionEngine, MarketEngine marketEngine,
        MatchEngine matchEngine, StockMapper stockMapper, OrderService orderService,
        TradingTimeUtil tradingTimeUtil) {
        this.callAuctionEngine = callAuctionEngine;
        this.marketEngine = marketEngine;
        this.matchEngine = matchEngine;
        this.stockMapper = stockMapper;
        this.orderService = orderService;
        this.tradingTimeUtil = tradingTimeUtil;
    }

    /**
     * 查询集合竞价快照
     */
    @Override
    public CallAuctionVO getSnapshot(String code) {
        StockQuoteVO quote = marketEngine.getQuote(code);
        if (quote == null) {
            throw new BusinessException("股票不存在");
        }
        LocalDateTime now = tradingTimeUtil.now();
        TradingPhase phase = tradingTimeUtil.getPhase(now);
        boolean closing = phase == TradingPhase.CLOSE_AUCTION
            || (!now.toLocalTime().isBefore(LocalTime.of(15, 0)));
        AuctionSnapshot snapshot = closing
            ? callAuctionEngine.snapshotClosing(code, quote.getPreClose())
            : callAuctionEngine.snapshotOpening(code, quote.getPreClose());
        return toVO(snapshot, phase, now);
    }

    /**
     * 关闭开盘集合竞价并结算成交，未成交委托暂存到 9:30
     */
    @Override
    public void closeOpeningAuction(LocalDateTime closeTime) {
        for (String code : callAuctionEngine.openingCodes()) {
            StockQuoteVO quote = marketEngine.getQuote(code);
            if (quote == null) {
                continue;
            }
            AuctionResult result = callAuctionEngine.closeOpening(code, quote.getPreClose());
            if (result == null) {
                continue;
            }
            settleAndCarry(code, result, closeTime);
            for (EngineOrder order : result.getResidualOrders()) {
                callAuctionEngine.acceptPending(order);
            }
            log.info("开盘集合竞价完成：code={}, price={}, volume={}, residual={}",
                code, result.getPrice(), result.getMatchedVolume(),
                result.getResidualOrders().size());
        }
    }

    /**
     * 9:30 释放竞价未成交/冻结委托到连续交易
     */
    @Override
    public void releaseOpeningOrders() {
        for (String code : callAuctionEngine.pendingCodes()) {
            List<EngineOrder> orders = callAuctionEngine.drainPending(code);
            for (EngineOrder order : orders) {
                if (order.getRemainingQuantity() > 0) {
                    orderService.releasePendingOrder(order);
                }
            }
        }
    }

    /**
     * 14:57 连续交易未成交委托转入收盘集合竞价
     */
    @Override
    public void prepareClosingAuction() {
        List<EngineOrder> orders = matchEngine.drainAllOrders();
        if (!orders.isEmpty()) {
            callAuctionEngine.addClosingOrders(orders);
            log.info("收盘集合竞价转入连续交易委托 {} 笔", orders.size());
        }
    }

    /**
     * 关闭收盘集合竞价并结算，未成交委托撤销
     */
    @Override
    public void closeClosingAuction(LocalDateTime closeTime) {
        for (String code : callAuctionEngine.closingCodes()) {
            StockQuoteVO quote = marketEngine.getQuote(code);
            if (quote == null) {
                continue;
            }
            AuctionResult result = callAuctionEngine.closeClosing(code, quote.getPreClose());
            if (result == null) {
                continue;
            }
            settleAndCarry(code, result, closeTime);
            for (EngineOrder order : result.getResidualOrders()) {
                orderService.cancelAuctionResidual(order);
            }
            log.info("收盘集合竞价完成：code={}, price={}, volume={}, residual={}",
                code, result.getPrice(), result.getMatchedVolume(),
                result.getResidualOrders().size());
        }
    }

    @Override
    public void clearDay() {
        callAuctionEngine.clearAll();
    }

    private void settleAndCarry(String code, AuctionResult result, LocalDateTime closeTime) {
        if (result.getMatches() == null || result.getMatches().isEmpty()) {
            return;
        }
        Stock stock = stockMapper.getStockByCode(code);
        if (stock == null) {
            return;
        }
        orderService.settleAuction(result.getMatches(), stock, closeTime);
    }

    private CallAuctionVO toVO(AuctionSnapshot snapshot, TradingPhase phase,
        LocalDateTime updateTime) {
        return CallAuctionVO.builder()
            .code(snapshot.getCode())
            .phase(phase.getCode())
            .phaseName(phase.getLabel())
            .indicativePrice(snapshot.getIndicativePrice())
            .matchedVolume(hands(snapshot.getMatchedVolume()))
            .unmatchedBuyVolume(hands(snapshot.getUnmatchedBuyVolume()))
            .unmatchedSellVolume(hands(snapshot.getUnmatchedSellVolume()))
            .bids(snapshot.getBids())
            .asks(snapshot.getAsks())
            .updateTime(updateTime)
            .build();
    }

    private BigDecimal hands(long shares) {
        return BigDecimal.valueOf(shares).movePointLeft(2);
    }
}
