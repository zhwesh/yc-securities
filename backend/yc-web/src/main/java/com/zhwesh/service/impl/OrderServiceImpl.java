package com.zhwesh.service.impl;

import com.zhwesh.DTO.CancelOrderDTO;
import com.zhwesh.DTO.CreateOrderDTO;
import com.zhwesh.VO.OrderVO;
import com.zhwesh.VO.StockQuoteVO;
import com.zhwesh.VO.StockTradeDetailVO;
import com.zhwesh.VO.TradeVO;
import com.zhwesh.constant.AccountStatus;
import com.zhwesh.constant.OrderStatus;
import com.zhwesh.constant.OrderType;
import com.zhwesh.constant.TradingPhase;
import com.zhwesh.engine.auction.AuctionMatch;
import com.zhwesh.engine.auction.CallAuctionEngine;
import com.zhwesh.engine.market.MarketEngine;
import com.zhwesh.engine.match.MatchEngine;
import com.zhwesh.engine.model.EngineOrder;
import com.zhwesh.engine.model.EngineOrderType;
import com.zhwesh.engine.model.EngineTrade;
import com.zhwesh.engine.model.OrderSide;
import com.zhwesh.entity.Account;
import com.zhwesh.entity.Order;
import com.zhwesh.entity.SoldOrder;
import com.zhwesh.entity.Stock;
import com.zhwesh.exception.BusinessException;
import com.zhwesh.mapper.AccountMapper;
import com.zhwesh.mapper.OrderMapper;
import com.zhwesh.mapper.PositionMapper;
import com.zhwesh.mapper.StockMapper;
import com.zhwesh.properties.TradeFeeProperties;
import com.zhwesh.result.PageResult;
import com.zhwesh.security.UserContext;
import com.zhwesh.service.OrderService;
import com.zhwesh.util.PageUtil;
import com.zhwesh.util.StockUtil;
import com.zhwesh.util.TradeFeeUtil;
import com.zhwesh.util.TradingTimeUtil;
import com.zhwesh.websocket.MarketWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private static final DateTimeFormatter TRADE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("HH:mm:ss");

    private final OrderMapper orderMapper;
    private final AccountMapper accountMapper;
    private final StockMapper stockMapper;
    private final PositionMapper positionMapper;
    private final MatchEngine matchEngine;
    private final MarketEngine marketEngine;
    private final TradeFeeProperties feeProperties;
    private final MarketWebSocketHandler webSocketHandler;
    private final TradingTimeUtil tradingTimeUtil;
    private final CallAuctionEngine callAuctionEngine;

    @Autowired
    public OrderServiceImpl(OrderMapper orderMapper, AccountMapper accountMapper,
        StockMapper stockMapper, PositionMapper positionMapper,
        MatchEngine matchEngine, MarketEngine marketEngine,
        TradeFeeProperties feeProperties, MarketWebSocketHandler webSocketHandler,
        TradingTimeUtil tradingTimeUtil, CallAuctionEngine callAuctionEngine) {
        this.orderMapper = orderMapper;
        this.accountMapper = accountMapper;
        this.stockMapper = stockMapper;
        this.positionMapper = positionMapper;
        this.matchEngine = matchEngine;
        this.marketEngine = marketEngine;
        this.feeProperties = feeProperties;
        this.webSocketHandler = webSocketHandler;
        this.tradingTimeUtil = tradingTimeUtil;
        this.callAuctionEngine = callAuctionEngine;
    }

    /**
     * 分页查询成交记录
     * @param accountId 资金账户id
     * @param code 股票代码
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param pageNum 页号
     * @param pageSize 每页大小
     * @return 分页查询结果
     */
    @Override
    public PageResult getTrades(Long accountId, String code,
        LocalDateTime startTime, LocalDateTime endTime,
        Integer pageNum, Integer pageSize) {
        getOwnedAccount(accountId);
        if (code != null && !code.isEmpty()) {
            Stock stock = stockMapper.getStockByCode(code);
            if (stock == null) {
                throw new BusinessException("股票不存在");
            }
        }

        int offset = PageUtil.getOffset(pageNum, pageSize);
        int limit = PageUtil.getPageSize(pageSize);
        List<SoldOrder> soldOrders = orderMapper.getSoldOrders(
            accountId, code, startTime, endTime, offset, limit
        );

        Map<String, String> stockNames = getStockNames(
            soldOrders.stream().map(SoldOrder::getStockCode).toList());

        List<TradeVO> tradeVOs = new ArrayList<>();
        for (SoldOrder soldOrder : soldOrders) {
            TradeVO tradeVO = new TradeVO();
            BeanUtils.copyProperties(soldOrder, tradeVO);
            tradeVO.setStockName(stockNames.get(soldOrder.getStockCode()));
            tradeVO.setAmount(
                soldOrder.getPrice()
                    .multiply(new BigDecimal(soldOrder.getQuantity()))
            );
            tradeVOs.add(tradeVO);
        }

        long total = orderMapper.getSoldOrdersCount(accountId, code, startTime, endTime);
        return new PageResult(total, tradeVOs);
    }

    /**
     * 分页查询委托单
     * @param accountId 资金账户id
     * @param code 股票代码
     * @param status 委托单状态
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param pageNum 页号
     * @param pageSize 每页大小
     * @return 分页查询结果
     */
    @Override
    public PageResult getOrders(Long accountId, String code,
        Integer status, LocalDateTime startTime, LocalDateTime endTime,
        Integer pageNum, Integer pageSize) {
        getOwnedAccount(accountId);
        if (code != null && !code.isEmpty()) {
            Stock stock = stockMapper.getStockByCode(code);
            if (stock == null) {
                throw new BusinessException("股票不存在");
            }
        }

        int offset = PageUtil.getOffset(pageNum, pageSize);
        int limit = PageUtil.getPageSize(pageSize);
        List<Order> orders = orderMapper.getOrders(
            accountId, code, status, startTime, endTime, offset, limit
        );

        Map<String, String> stockNames = getStockNames(
            orders.stream().map(Order::getStockCode).toList());

        List<OrderVO> orderVOs = new ArrayList<>();
        for (Order order : orders) {
            OrderVO orderVO = new OrderVO();
            BeanUtils.copyProperties(order, orderVO);
            orderVO.setStockName(stockNames.get(order.getStockCode()));
            orderVOs.add(orderVO);
        }

        long total = orderMapper.getOrdersCount(accountId, code, status, startTime, endTime);
        return new PageResult(total, orderVOs);
    }

    /**
     * 创建委托单
     * @param createOrderDTO 委托参数
     * @return 委托单信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO createOrder(CreateOrderDTO createOrderDTO) {
        Integer quantity = createOrderDTO.getQuantity();
        if (quantity == null || quantity == 0) {
            throw new BusinessException("委托数量不得为0");
        }
        if (Math.abs(quantity) % 100 != 0) {
            throw new BusinessException("委托数量必须为100的整数倍");
        }
        Integer type = createOrderDTO.getType();
        if (!OrderType.LIMIT_ORDER.equals(type) && !OrderType.MARKET_ORDER.equals(type)) {
            throw new BusinessException("委托类型不合法");
        }
        if (createOrderDTO.getOrderKey() == null || createOrderDTO.getOrderKey().isBlank()) {
            throw new BusinessException("幂等键不能为空");
        }
        if (createOrderDTO.getOrderKey().length() > 36) {
            throw new BusinessException("幂等键不能超过36个字符");
        }

        LocalDateTime now = tradingTimeUtil.now();
        TradingPhase phase = tradingTimeUtil.getPhase(now);
        if (TradingPhase.CLOSED == phase) {
            throw new BusinessException("当前不在交易时段");
        }
        boolean auctionPhase = phase.isAuction();
        if (auctionPhase && OrderType.MARKET_ORDER.equals(type)) {
            throw new BusinessException("集合竞价阶段仅支持限价委托");
        }

        Account account = getOwnedAccount(createOrderDTO.getAccountId());
        if (AccountStatus.DISABLE.equals(account.getStatus())) {
            throw new BusinessException("资金账户已被冻结");
        }

        Stock stock = stockMapper.getStockByCode(createOrderDTO.getCode());
        if (stock == null) {
            throw new BusinessException("股票不存在");
        }
        if (!StockUtil.supportsBoard(account.getBoards(), stock.getBoard())) {
            throw new BusinessException("账户未开通该板块交易权限");
        }

        StockQuoteVO quote = marketEngine.getQuote(createOrderDTO.getCode());
        if (quote == null || quote.getPrice() == null) {
            throw new BusinessException("行情数据异常");
        }

        BigDecimal limitPercent = StockUtil.getLimitPercent(stock.getBoard());
        BigDecimal priceBase = quote.getPreClose() != null ? quote.getPreClose() : quote.getPrice();
        BigDecimal price = createOrderDTO.getPrice();
        BigDecimal freezePrice;
        if (OrderType.LIMIT_ORDER.equals(type)) {
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("限价单价格必须大于0");
            }
            BigDecimal maxPrice = priceBase
                .multiply(BigDecimal.ONE.add(limitPercent))
                .setScale(2, RoundingMode.HALF_UP);
            BigDecimal minPrice = priceBase
                .multiply(BigDecimal.ONE.subtract(limitPercent))
                .setScale(2, RoundingMode.HALF_UP);
            if (price.compareTo(maxPrice) > 0 || price.compareTo(minPrice) < 0) {
                throw new BusinessException("定价超过最大涨跌幅");
            }
            price = price.setScale(2, RoundingMode.HALF_UP);
            freezePrice = price;
        } else {
            price = null;
            freezePrice = priceBase
                .multiply(BigDecimal.ONE.add(limitPercent))
                .setScale(2, RoundingMode.HALF_UP);
        }

        Order existing = orderMapper.getOrderByOrderKey(createOrderDTO.getOrderKey());
        if (existing != null) {
            return Order2OrderVO(existing);
        }

        boolean isBuy = quantity > 0;
        long absQuantity = Math.abs((long) quantity);
        BigDecimal frozenAssets = null;
        if (isBuy) {
            frozenAssets = freezeAmount(freezePrice, absQuantity);
            if (accountMapper.freezeAssets(account.getId(), frozenAssets) == 0) {
                throw new BusinessException("可用资金不足");
            }
        } else {
            if (positionMapper.freezePosition(account.getId(), stock.getCode(), absQuantity) == 0) {
                throw new BusinessException("可卖数量不足");
            }
        }

        Order order = new Order();
        BeanUtils.copyProperties(createOrderDTO, order);
        order.setStockCode(createOrderDTO.getCode());
        order.setPrice(price);
        order.setFreezePrice(isBuy ? freezePrice : null);
        order.setSoldQuantity(0);
        order.setStatus(OrderStatus.NOT_SOLD);
        order.setCreateTime(now);

        try {
            orderMapper.insertOrder(order);
        } catch (DuplicateKeyException e) {
            if (isBuy) {
                accountMapper.unfreezeAssets(account.getId(), frozenAssets);
            } else {
                positionMapper.unfreezePosition(account.getId(), stock.getCode(), absQuantity);
            }
            Order duplicated = orderMapper.getOrderByOrderKey(createOrderDTO.getOrderKey());
            if (duplicated == null) {
                throw new BusinessException("委托提交失败，请重试");
            }
            return Order2OrderVO(duplicated);
        }

        EngineOrder engineOrder = new EngineOrder();
        BeanUtils.copyProperties(order, engineOrder);
        engineOrder.setCode(order.getStockCode());
        engineOrder.setId(order.getId());
        engineOrder.setSide(isBuy ? OrderSide.BUY : OrderSide.SELL);
        engineOrder.setFreezePrice(isBuy ? freezePrice : null);
        engineOrder.setQuantity(absQuantity);
        engineOrder.setFilledQuantity(0);
        engineOrder.setType(OrderType.LIMIT_ORDER.equals(type)
            ? EngineOrderType.LIMIT : EngineOrderType.MARKET);
        engineOrder.setOrderTime(now);

        if (auctionPhase) {
            if (TradingPhase.CLOSE_AUCTION == phase) {
                callAuctionEngine.acceptClosing(engineOrder);
            } else if (TradingPhase.OPEN_AUCTION_FREEZE == phase) {
                callAuctionEngine.acceptPending(engineOrder);
            } else {
                callAuctionEngine.acceptOpening(engineOrder);
            }
            OrderVO auctionOrder = Order2OrderVO(orderMapper.getOrderById(order.getId()));
            webSocketHandler.pushOrderUpdate(account.getId(), auctionOrder);
            return auctionOrder;
        }

        List<EngineTrade> trades = matchEngine.placeOrder(engineOrder);
        settleTrades(engineOrder, isBuy ? freezePrice : null, trades, stock);

        if (OrderType.MARKET_ORDER.equals(type)) {
            long remaining = engineOrder.getRemainingQuantity();
            if (remaining > 0) {
                if (isBuy) {
                    accountMapper.unfreezeAssets(account.getId(),
                        freezeAmount(freezePrice, remaining));
                } else {
                    positionMapper.unfreezePosition(account.getId(), stock.getCode(), remaining);
                }
                orderMapper.updateStatusIfActive(order.getId(), OrderStatus.CANCELED);
            }
        }

        OrderVO orderVO = Order2OrderVO(orderMapper.getOrderById(order.getId()));
        webSocketHandler.pushOrderUpdate(account.getId(), orderVO);
        return orderVO;
    }

    /**
     * 查询委托详情
     * @param id 委托单id
     * @return 委托单信息
     */
    @Override
    public OrderVO getOrderById(Long id) {
        Order order = orderMapper.getOrderById(id);
        if (order == null) {
            throw new BusinessException("委托单不存在");
        }
        getOwnedAccount(order.getAccountId());
        return Order2OrderVO(order);
    }

    /**
     * 撤单
     * @param cancelOrderDTO 所需信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(CancelOrderDTO cancelOrderDTO) {
        Account account = getOwnedAccount(cancelOrderDTO.getAccountId());

        Order order = orderMapper.getOrderById(cancelOrderDTO.getOrderId());
        if (order == null) {
            throw new BusinessException("委托单不存在");
        }
        if (!order.getAccountId().equals(account.getId())) {
            throw new BusinessException("委托单不属于该资金账户");
        }
        if (OrderStatus.CANCELED.equals(order.getStatus())) {
            throw new BusinessException("委托单已撤销");
        }
        if (OrderStatus.SOLD.equals(order.getStatus())) {
            throw new BusinessException("委托单已全部成交，无法撤销");
        }

        TradingPhase phase = tradingTimeUtil.getPhase();
        if (TradingPhase.CLOSED == phase) {
            throw new BusinessException("当前不在交易时段");
        }
        if (TradingPhase.OPEN_AUCTION_LOCKED == phase
            || TradingPhase.OPEN_AUCTION_FREEZE == phase
            || TradingPhase.CLOSE_AUCTION == phase) {
            throw new BusinessException("当前阶段不可撤单");
        }

        if (orderMapper.updateStatusIfActive(order.getId(), OrderStatus.CANCELED) == 0) {
            throw new BusinessException("委托单状态已变更，请刷新后重试");
        }

        long remaining = Math.abs((long) order.getQuantity()) - order.getSoldQuantity();
        if (remaining > 0) {
            if (order.getQuantity() > 0) {
                if (order.getFreezePrice() != null) {
                    accountMapper.unfreezeAssets(order.getAccountId(),
                        freezeAmount(order.getFreezePrice(), remaining));
                }
            } else {
                positionMapper.unfreezePosition(
                    order.getAccountId(), order.getStockCode(), remaining);
            }
        }

        matchEngine.cancelOrder(order.getStockCode(), order.getId());
        callAuctionEngine.cancel(order.getStockCode(), order.getId());
        webSocketHandler.pushOrderUpdate(order.getAccountId(),
            Order2OrderVO(orderMapper.getOrderById(order.getId())));
    }

    /**
     * 撤销全部未成交或部分成交委托
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelAllPendingOrders() {
        cancelActiveOrders(orderMapper.getActiveOrders());
        callAuctionEngine.clearAll();
    }

    /**
     * 撤销指定日期前未成交或部分成交委托
     * @param today 当前日期
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelPendingOrdersBefore(LocalDate today) {
        cancelActiveOrders(orderMapper.getActiveOrdersBefore(today));
    }

    /**
     * 撤销未成交或部分成交委托
     * @param orders 委托列表
     */
    private void cancelActiveOrders(List<Order> orders) {
        for (Order order : orders) {
            long remaining = Math.abs((long) order.getQuantity()) - order.getSoldQuantity();
            if (remaining > 0) {
                if (order.getQuantity() > 0) {
                    if (order.getFreezePrice() != null) {
                        accountMapper.unfreezeAssets(order.getAccountId(),
                            freezeAmount(order.getFreezePrice(), remaining));
                    }
                } else {
                    positionMapper.unfreezePosition(
                        order.getAccountId(), order.getStockCode(), remaining);
                }
            }
            matchEngine.cancelOrder(order.getStockCode(), order.getId());
            orderMapper.updateStatusIfActive(order.getId(), OrderStatus.CANCELED);
            log.info("跨日撤销委托：orderId={}, code={}", order.getId(), order.getStockCode());
        }
    }

    /**
     * 结算集合竞价成交
     * @param matches 成交配对
     * @param stock 股票信息
     * @param tradeTime 成交时间
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void settleAuction(List<AuctionMatch> matches, Stock stock, LocalDateTime tradeTime) {
        if (matches == null || matches.isEmpty()) {
            return;
        }
        for (AuctionMatch match : matches) {
            EngineOrder buyer = match.getBuyer();
            EngineOrder seller = match.getSeller();
            if (match.getQuantity() <= 0) {
                continue;
            }
            EngineTrade trade = EngineTrade.builder()
                .takerOrderId(buyer.getId())
                .makerOrderId(seller.getId())
                .takerAccountId(buyer.getAccountId())
                .makerAccountId(seller.getAccountId())
                .code(stock.getCode())
                .takerSide(OrderSide.BUY)
                .price(match.getPrice())
                .quantity(match.getQuantity())
                .tradeTime(tradeTime)
                .build();
            matchEngine.recordAuctionTrade(trade);
            BigDecimal buyerFreezePrice = buyer.getFreezePrice() == null
                ? match.getPrice() : buyer.getFreezePrice();
            settleTrades(buyer, buyerFreezePrice, List.of(trade), stock);

            Order buyerOrder = orderMapper.getOrderById(buyer.getId());
            if (buyerOrder != null) {
                webSocketHandler.pushOrderUpdate(buyer.getAccountId(),
                    Order2OrderVO(buyerOrder));
            }
        }
    }

    /**
     * 释放集合竞价冻结委托
     * @param order 已冻结委托
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releasePendingOrder(EngineOrder order) {
        Stock stock = stockMapper.getStockByCode(order.getCode());
        if (stock == null || order.getRemainingQuantity() <= 0) {
            return;
        }
        List<EngineTrade> trades = matchEngine.placeOrder(order);
        settleTrades(order, order.getFreezePrice(), trades, stock);

        Order dbOrder = orderMapper.getOrderById(order.getId());
        if (dbOrder != null) {
            webSocketHandler.pushOrderUpdate(order.getAccountId(), Order2OrderVO(dbOrder));
        }
    }

    /**
     * 撤销收盘集合竞价未成交委托
     * @param engineOrder 未成交委托
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelAuctionResidual(EngineOrder engineOrder) {
        Order order = orderMapper.getOrderById(engineOrder.getId());
        if (order == null || OrderStatus.CANCELED.equals(order.getStatus())
            || OrderStatus.SOLD.equals(order.getStatus())) {
            return;
        }
        long remaining = Math.abs((long) order.getQuantity()) - order.getSoldQuantity();
        if (orderMapper.updateStatusIfActive(order.getId(), OrderStatus.CANCELED) == 0) {
            return;
        }
        if (remaining > 0) {
            if (order.getQuantity() > 0) {
                BigDecimal freezePrice = order.getFreezePrice() != null
                    ? order.getFreezePrice() : engineOrder.getFreezePrice();
                if (freezePrice != null) {
                    accountMapper.unfreezeAssets(order.getAccountId(),
                        freezeAmount(freezePrice, remaining));
                }
            } else {
                positionMapper.unfreezePosition(order.getAccountId(), order.getStockCode(),
                    remaining);
            }
        }
        webSocketHandler.pushOrderUpdate(order.getAccountId(),
            Order2OrderVO(orderMapper.getOrderById(order.getId())));
    }

    /**
     * 获取买入冻结金额
     * @param freezePrice 冻结价格
     * @param quantity 数量
     * @return 冻结金额
     */
    private BigDecimal freezeAmount(BigDecimal freezePrice, long quantity) {
        BigDecimal amount = freezePrice.multiply(BigDecimal.valueOf(quantity));
        return amount.add(TradeFeeUtil.buyFee(amount, feeProperties));
    }

    /**
     * 结算成交
     * @param taker 主动单
     * @param takerFreezePrice 主动买入单冻结价
     * @param trades 成交列表
     * @param stock 股票信息
     */
    private void settleTrades(EngineOrder taker, BigDecimal takerFreezePrice,
        List<EngineTrade> trades, Stock stock) {
        List<SoldOrder> soldOrders = new ArrayList<>();
        for (EngineTrade trade : trades) {
            boolean takerIsBuyer = trade.getTakerSide() == OrderSide.BUY;
            long tradeQuantity = trade.getQuantity();
            BigDecimal price = trade.getPrice();
            BigDecimal cost = price.multiply(BigDecimal.valueOf(tradeQuantity));
            Integer takerSide = takerIsBuyer ? 0 : 1;

            Long buyerOrderId = takerIsBuyer ? trade.getTakerOrderId() : trade.getMakerOrderId();
            Long sellerOrderId = takerIsBuyer ? trade.getMakerOrderId() : trade.getTakerOrderId();
            Long buyerAccountId = takerIsBuyer ? trade.getTakerAccountId() : trade.getMakerAccountId();
            Long sellerAccountId = takerIsBuyer ? trade.getMakerAccountId() : trade.getTakerAccountId();
            BigDecimal buyerFreezePrice = takerIsBuyer ? takerFreezePrice : price;

            BigDecimal frozenAmount = buyerFreezePrice.multiply(BigDecimal.valueOf(tradeQuantity));
            frozenAmount = frozenAmount.add(TradeFeeUtil.buyFee(frozenAmount, feeProperties));
            BigDecimal buyCost = cost.add(TradeFeeUtil.buyFee(cost, feeProperties));
            if (accountMapper.settleBuy(buyerAccountId, frozenAmount, buyCost) == 0) {
                throw new BusinessException("买入冻结资金不足，成交结算失败");
            }
            addPosition(buyerAccountId, stock, price, tradeQuantity);

            BigDecimal sellNet = cost.subtract(TradeFeeUtil.sellFee(cost, feeProperties));
            accountMapper.addAssets(sellerAccountId, sellNet);
            if (positionMapper.decreasePosition(sellerAccountId, stock.getCode(), tradeQuantity) == 0) {
                throw new BusinessException("卖出持仓不足，成交结算失败");
            }

            orderMapper.increaseSoldQuantity(buyerOrderId, (int) tradeQuantity);
            orderMapper.increaseSoldQuantity(sellerOrderId, (int) tradeQuantity);

            LocalDateTime tradeTime = trade.getTradeTime();
            soldOrders.add(SoldOrder.builder()
                .orderId(buyerOrderId)
                .accountId(buyerAccountId)
                .stockCode(stock.getCode())
                .price(price)
                .quantity((int) tradeQuantity)
                .takerSide(takerSide)
                .createTime(tradeTime)
                .build());
            soldOrders.add(SoldOrder.builder()
                .orderId(sellerOrderId)
                .accountId(sellerAccountId)
                .stockCode(stock.getCode())
                .price(price)
                .quantity(-(int) tradeQuantity)
                .takerSide(takerSide)
                .createTime(tradeTime)
                .build());

            marketEngine.onTrade(stock.getCode(), price, tradeQuantity);

            webSocketHandler.pushTrade(stock.getCode(), StockTradeDetailVO.builder()
                .time(tradeTime.format(TRADE_TIME_FORMATTER))
                .price(price)
                .volume(tradeQuantity)
                .direction(takerIsBuyer ? 0 : 1)
                .build());

            Order makerOrder = orderMapper.getOrderById(trade.getMakerOrderId());
            if (makerOrder != null) {
                webSocketHandler.pushOrderUpdate(
                    trade.getMakerAccountId(), Order2OrderVO(makerOrder));
            }
        }
        if (!soldOrders.isEmpty()) {
            orderMapper.batchInsertSoldOrders(soldOrders);
        }
    }

    /**
     * 增加持仓
     * @param accountId 资金账户id
     * @param stock 股票
     * @param price 成交价格
     * @param quantity 成交数量
     */
    private void addPosition(Long accountId, Stock stock, BigDecimal price, long quantity) {
        if (positionMapper.increasePosition(accountId, stock.getCode(), price, quantity) == 0) {
            try {
                positionMapper.insertPosition(
                    accountId, stock.getCode(), stock.getName(), price, quantity);
            } catch (DuplicateKeyException e) {
                positionMapper.increasePosition(accountId, stock.getCode(), price, quantity);
            }
        }
    }

    /**
     * 获取当前用户资金账户
     * @param accountId 资金账户id
     * @return 资金账户
     */
    private Account getOwnedAccount(Long accountId) {
        Account account = accountMapper.getById(accountId);
        if (account == null) {
            throw new BusinessException("资金账户不存在");
        }
        if (!account.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException("资金账户不属于当前用户");
        }
        return account;
    }

    /**
     * 批量获取股票名称
     * @param codes 股票代码列表
     * @return 股票代码与名称映射
     */
    private Map<String, String> getStockNames(List<String> codes) {
        Map<String, String> stockNames = new HashMap<>();
        if (codes == null || codes.isEmpty()) {
            return stockNames;
        }
        List<String> distinctCodes = codes.stream()
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (distinctCodes.isEmpty()) {
            return stockNames;
        }
        for (Stock stock : stockMapper.getStocksByCodes(distinctCodes)) {
            stockNames.put(stock.getCode(), stock.getName());
        }
        return stockNames;
    }

    /**
     * 转换委托单
     * @param order 委托单
     * @return 委托单信息
     */
    private OrderVO Order2OrderVO(Order order) {
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(order, orderVO);
        Stock stock = stockMapper.getStockByCode(order.getStockCode());
        if (stock != null) {
            orderVO.setStockName(stock.getName());
        }
        return orderVO;
    }
}
