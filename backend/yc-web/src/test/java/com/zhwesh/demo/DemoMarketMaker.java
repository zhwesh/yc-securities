package com.zhwesh.demo;

import com.zhwesh.DTO.CancelOrderDTO;
import com.zhwesh.DTO.CreateOrderDTO;
import com.zhwesh.VO.OrderVO;
import com.zhwesh.VO.StockQuoteVO;
import com.zhwesh.constant.AccountStatus;
import com.zhwesh.constant.Board;
import com.zhwesh.constant.OrderStatus;
import com.zhwesh.constant.OrderType;
import com.zhwesh.engine.market.MarketEngine;
import com.zhwesh.engine.match.MatchEngine;
import com.zhwesh.entity.Account;
import com.zhwesh.entity.Order;
import com.zhwesh.entity.Position;
import com.zhwesh.entity.Stock;
import com.zhwesh.entity.User;
import com.zhwesh.mapper.AccountMapper;
import com.zhwesh.mapper.OrderMapper;
import com.zhwesh.mapper.PositionMapper;
import com.zhwesh.mapper.StockMapper;
import com.zhwesh.mapper.UserMapper;
import com.zhwesh.service.OrderService;
import com.zhwesh.util.TradingTimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 演示做市机器人
 */
@Slf4j
@Component
@Profile("demo")
@ConditionalOnProperty(name = "demo.market-maker.enabled", havingValue = "true")
public class DemoMarketMaker {

    /**
     * 挂单保留时间，单位毫秒
     */
    private static final long PENDING_ORDER_TTL_MS = 5_000L;

    /**
     * A股最小变动价位
     */
    private static final BigDecimal TICK = new BigDecimal("0.01");

    /**
     * 机器人初始可用资金
     */
    private static final BigDecimal BOT_INITIAL_CASH = new BigDecimal("10000000");

    /**
     * 机器人初始持仓数量，单位股
     */
    private static final long BOT_INITIAL_QUANTITY = 1_000_000L;

    /**
     * admin演示账户初始可用资金
     */
    private static final BigDecimal ADMIN_INITIAL_CASH = new BigDecimal("1000000");

    /**
     * admin演示账户初始持仓数量，单位股
     */
    private static final long ADMIN_INITIAL_QUANTITY = 10_000L;

    /**
     * 演示账户初始密码
     */
    private static final String DEMO_PASSWORD = "123456";

    /**
     * 单笔委托数量候选值，单位股
     */
    private static final int[] ORDER_QUANTITIES = {100, 200, 300, 500};

    private final DemoMarketMakerProperties properties;
    private final MarketEngine marketEngine;
    private final MatchEngine matchEngine;
    private final OrderService orderService;
    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final AccountMapper accountMapper;
    private final PositionMapper positionMapper;
    private final StockMapper stockMapper;
    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder;
    private final TradingTimeUtil tradingTimeUtil;

    /**
     * 机器人列表
     */
    private final List<Bot> bots = new ArrayList<>();

    /**
     * 机器人待撤委托
     */
    private final Map<Long, PendingOrder> pendingOrders = new ConcurrentHashMap<>();

    /**
     * 初始化完成标记
     */
    private volatile boolean initialized;

    /**
     * 机器人参考价
     */
    private volatile BigDecimal fairPrice;

    /**
     * 上次市场最新价
     */
    private BigDecimal lastObservedPrice;

    /**
     * 短期方向，1上涨，-1下跌，0无趋势
     */
    private int trend;

    /**
     * 趋势剩余tick数
     */
    private int trendTicks;

    @Autowired
    public DemoMarketMaker(DemoMarketMakerProperties properties, MarketEngine marketEngine,
        MatchEngine matchEngine, OrderService orderService, OrderMapper orderMapper,
        UserMapper userMapper, AccountMapper accountMapper, PositionMapper positionMapper,
        StockMapper stockMapper, JdbcTemplate jdbcTemplate,
        BCryptPasswordEncoder passwordEncoder, TradingTimeUtil tradingTimeUtil) {
        this.properties = properties;
        this.marketEngine = marketEngine;
        this.matchEngine = matchEngine;
        this.orderService = orderService;
        this.orderMapper = orderMapper;
        this.userMapper = userMapper;
        this.accountMapper = accountMapper;
        this.positionMapper = positionMapper;
        this.stockMapper = stockMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.tradingTimeUtil = tradingTimeUtil;
    }

    /**
     * 初始化做市机器人
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        try {
            if (!properties.isEnabled()) {
                return;
            }
            String code = properties.getCode() == null ? "" : properties.getCode().trim();
            Stock stock = stockMapper.getStockByCode(code);
            if (stock == null) {
                log.warn("做市机器人未启动：股票不存在，code={}", code);
                return;
            }
            BigDecimal costPrice = resolveCostPrice(code);
            StockQuoteVO initialQuote = marketEngine.getQuote(code);
            BigDecimal initialPrice = initialQuote != null && initialQuote.getPrice() != null
                ? initialQuote.getPrice() : costPrice;
            fairPrice = initialPrice;
            lastObservedPrice = initialPrice;

            String password = passwordEncoder.encode(DEMO_PASSWORD);
            int botCount = Math.max(1, properties.getBots());
            for (int i = 1; i <= botCount; i++) {
                bots.add(ensureBot(i, stock, costPrice, password));
            }
            ensureAdminAccount(stock, costPrice);

            cancelExistingBotOrders();
            initialized = true;
            log.info("做市机器人已启动：{} 个，标的 {}，间隔 {}ms", bots.size(), code,
                properties.getIntervalMs());
        } catch (Exception e) {
            log.error("做市机器人初始化失败", e);
        }
    }

    /**
     * 执行做市交易
     */
    @Scheduled(fixedDelayString = "${demo.market-maker.interval-ms:500}")
    public void tick() {
        if (!initialized || bots.isEmpty() || !tradingTimeUtil.isTradingTime()) {
            return;
        }
        try {
            StockQuoteVO quote = marketEngine.getQuote(properties.getCode().trim());
            if (quote == null || quote.getPrice() == null) {
                return;
            }
            updateFairPrice(quote);
            cancelStaleOrders();

            ThreadLocalRandom random = ThreadLocalRandom.current();
            int makerIndex = random.nextInt(bots.size());
            int takerIndex = bots.size() == 1 ? makerIndex
                : (makerIndex + 1 + random.nextInt(bots.size() - 1)) % bots.size();
            Bot maker = bots.get(makerIndex);
            Bot taker = bots.get(takerIndex);
            BigDecimal center = fairPrice == null ? quote.getPrice() : fairPrice;
            runAs(maker.userId, () -> postQuotes(maker, quote, center));
            runAs(taker.userId, () -> takeLiquidity(taker, quote));
        } catch (Exception e) {
            log.warn("做市机器人执行异常：{}", e.getMessage());
        }
    }

    /**
     * 创建或获取机器人用户、资金账户和持仓
     * @param index 机器人序号
     * @param stock 股票信息
     * @param costPrice 成本价
     * @param password 初始密码
     * @return 机器人
     */
    private Bot ensureBot(int index, Stock stock, BigDecimal costPrice, String password) {
        String username = String.format("mm_bot_%02d", index);
        User user = userMapper.getByUsername(username);
        if (user == null) {
            user = User.builder()
                .password(password)
                .name("做市机器人" + index)
                .username(username)
                .phone(nextAvailableBotPhone(index))
                .build();
            userMapper.insertUser(user);
        }

        Account account;
        List<Account> accounts = accountMapper.getByUserId(user.getId());
        if (accounts == null || accounts.isEmpty()) {
            account = Account.builder()
                .password(password)
                .userId(user.getId())
                .availableAssets(BOT_INITIAL_CASH)
                .boards(Board.MAIN_BOARD)
                .status(AccountStatus.ENABLE)
                .build();
            accountMapper.insertAccount(account);
        } else {
            account = accounts.get(0);
        }

        Position position = positionMapper.getPosition(account.getId(), stock.getCode());
        if (position == null) {
            insertSeedPosition(account.getId(), stock, costPrice, BOT_INITIAL_QUANTITY);
        }
        return new Bot(user.getId(), account.getId());
    }

    /**
     * 创建或获取admin演示账户
     * @param stock 股票信息
     * @param costPrice 成本价
     */
    private void ensureAdminAccount(Stock stock, BigDecimal costPrice) {
        User admin = userMapper.getByUsername("admin");
        if (admin == null) {
            log.warn("未找到 admin 用户，跳过 admin 演示账户初始化");
            return;
        }
        List<Account> accounts = accountMapper.getByUserId(admin.getId());
        if (accounts != null && !accounts.isEmpty()) {
            Account account = accounts.get(0);
            if (positionMapper.getPosition(account.getId(), stock.getCode()) == null) {
                insertSeedPosition(account.getId(), stock, costPrice, ADMIN_INITIAL_QUANTITY);
                log.info("已为 admin 演示账户补充 {} 持仓 {} 股",
                    stock.getCode(), ADMIN_INITIAL_QUANTITY);
            }
            return;
        }
        Account account = Account.builder()
            .password(passwordEncoder.encode(DEMO_PASSWORD))
            .userId(admin.getId())
            .availableAssets(ADMIN_INITIAL_CASH)
            .boards(Board.MAIN_BOARD)
            .status(AccountStatus.ENABLE)
            .build();
        accountMapper.insertAccount(account);
        insertSeedPosition(account.getId(), stock, costPrice, ADMIN_INITIAL_QUANTITY);
        log.info("已为 admin 创建演示资金账户：可用资金 {}，{} 持仓 {} 股",
            ADMIN_INITIAL_CASH, stock.getCode(), ADMIN_INITIAL_QUANTITY);
    }

    /**
     * 分配机器人手机号
     * @param index 机器人序号
     * @return 可用手机号
     */
    private String nextAvailableBotPhone(int index) {
        int base = 10_000_000 + index;
        for (int i = 0; i < 10_000; i++) {
            String phone = String.format("199%08d", base + i);
            if (userMapper.getByPhone(phone) == null) {
                return phone;
            }
        }
        throw new IllegalStateException("无法为做市机器人分配手机号，index=" + index);
    }

    /**
     * 写入演示持仓
     * @param accountId 资金账户id
     * @param stock 股票信息
     * @param costPrice 成本价
     * @param quantity 持仓数量
     */
    private void insertSeedPosition(Long accountId, Stock stock, BigDecimal costPrice,
        long quantity) {
        jdbcTemplate.update("INSERT INTO `position`(account_id, stock_code, stock_name, " +
                "cost_price, quantity, available_quantity, today_buy_quantity) " +
                "VALUES (?, ?, ?, ?, ?, ?, 0)",
            accountId, stock.getCode(), stock.getName(), costPrice, quantity, quantity);
    }

    /**
     * 获取演示持仓成本价
     * @param code 股票代码
     * @return 成本价
     */
    private BigDecimal resolveCostPrice(String code) {
        StockQuoteVO quote = marketEngine.getQuote(code);
        if (quote != null && quote.getPreClose() != null) {
            return quote.getPreClose();
        }
        Stock stock = stockMapper.getStockByCode(code);
        if (stock != null && stock.getInitialPrice() != null) {
            return stock.getInitialPrice();
        }
        return BigDecimal.ONE;
    }

    /**
     * 更新机器人参考价
     * @param quote 行情快照
     */
    private void updateFairPrice(StockQuoteVO quote) {
        BigDecimal price = quote.getPrice();
        if (price == null) {
            return;
        }
        if (fairPrice == null) {
            fairPrice = price;
        }
        if (lastObservedPrice != null) {
            int cmp = price.compareTo(lastObservedPrice);
            if (cmp != 0) {
                fairPrice = price;
                trend = cmp > 0 ? 1 : -1;
                trendTicks = 6 + ThreadLocalRandom.current().nextInt(10);
            }
        }
        lastObservedPrice = price;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (trendTicks > 0) {
            trendTicks--;
        } else {
            trend = 0;
            if (random.nextInt(4) == 0) {
                int ticks = random.nextInt(-2, 3);
                fairPrice = fairPrice.add(TICK.multiply(BigDecimal.valueOf(ticks)));
            }
        }
        fairPrice = clampPrice(fairPrice, quote);
        reflectAtLimit(quote);
    }

    /**
     * 设置涨跌幅边界方向
     * @param quote 行情快照
     */
    private void reflectAtLimit(StockQuoteVO quote) {
        BigDecimal base = quote.getPreClose() != null ? quote.getPreClose() : quote.getPrice();
        if (base == null) {
            return;
        }
        BigDecimal ratio = maxDeviationRatio();
        BigDecimal max = base.multiply(BigDecimal.ONE.add(ratio));
        BigDecimal min = base.multiply(BigDecimal.ONE.subtract(ratio));
        if (fairPrice.compareTo(max) >= 0) {
            fairPrice = max;
            trend = -1;
            trendTicks = 5;
        } else if (fairPrice.compareTo(min) <= 0) {
            fairPrice = min;
            trend = 1;
            trendTicks = 5;
        }
    }

    /**
     * 提交双边报价
     * @param bot 机器人
     * @param quote 行情快照
     * @param center 报价中心价
     */
    private void postQuotes(Bot bot, StockQuoteVO quote, BigDecimal center) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        BigDecimal base = center == null ? quote.getPrice() : center;
        for (int level = 1; level <= 2; level++) {
            BigDecimal offset = TICK.multiply(BigDecimal.valueOf(level));
            int quantity = randomQuantity(random);
            placeOrder(bot, clampPrice(base.subtract(offset), quote), quantity);
            placeOrder(bot, clampPrice(base.add(offset), quote), -quantity);
        }
    }

    /**
     * 提交主动成交委托
     * @param bot 机器人
     * @param quote 行情快照
     */
    private void takeLiquidity(Bot bot, StockQuoteVO quote) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean buy;
        if (trend > 0) {
            buy = true;
        } else if (trend < 0) {
            buy = false;
        } else {
            buy = random.nextBoolean();
        }
        if (random.nextInt(100) < 10) {
            buy = random.nextBoolean();
        }

        int aggression = random.nextInt(0, 3);
        BigDecimal lastPrice = quote.getPrice();
        BigDecimal price;
        if (buy) {
            BigDecimal bestAsk = matchEngine.bestAsk(quote.getCode());
            price = (bestAsk != null ? bestAsk : lastPrice.add(TICK))
                .add(TICK.multiply(BigDecimal.valueOf(aggression)));
        } else {
            BigDecimal bestBid = matchEngine.bestBid(quote.getCode());
            price = (bestBid != null ? bestBid : lastPrice.subtract(TICK))
                .subtract(TICK.multiply(BigDecimal.valueOf(aggression)));
        }
        int quantity = randomQuantity(random);
        placeOrder(bot, clampPrice(price, quote), buy ? quantity : -quantity);
    }

    /**
     * 提交限价委托
     * @param bot 机器人
     * @param price 委托价格
     * @param quantity 委托数量
     */
    private void placeOrder(Bot bot, BigDecimal price, int quantity) {
        try {
            OrderVO order = orderService.createOrder(CreateOrderDTO.builder()
                .accountId(bot.accountId)
                .code(properties.getCode().trim())
                .type(OrderType.LIMIT_ORDER)
                .price(price)
                .quantity(quantity)
                .orderKey(UUID.randomUUID().toString())
                .build());
            if (OrderStatus.NOT_SOLD.equals(order.getStatus())
                || OrderStatus.PARTIALLY_SOLD.equals(order.getStatus())) {
                pendingOrders.put(order.getId(),
                    new PendingOrder(bot, order.getId(), System.currentTimeMillis()));
            }
        } catch (Exception e) {
            log.debug("做市机器人下单失败：accountId={}, price={}, quantity={}, reason={}",
                bot.accountId, price, quantity, e.getMessage());
        }
    }

    /**
     * 撤销超时挂单
     */
    private void cancelStaleOrders() {
        if (pendingOrders.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (PendingOrder pending : pendingOrders.values()) {
            if (now - pending.createTime < PENDING_ORDER_TTL_MS) {
                continue;
            }
            runAs(pending.bot.userId, () -> {
                try {
                    orderService.cancelOrder(CancelOrderDTO.builder()
                        .accountId(pending.bot.accountId)
                        .orderId(pending.orderId)
                        .build());
                } catch (Exception e) {
                    log.debug("做市机器人撤单失败：orderId={}, reason={}",
                        pending.orderId, e.getMessage());
                }
            });
            pendingOrders.remove(pending.orderId);
        }
    }

    /**
     * 撤销机器人遗留委托
     */
    private void cancelExistingBotOrders() {
        if (bots.isEmpty()) {
            return;
        }
        List<Long> accountIds = bots.stream().map(bot -> bot.accountId).toList();
        Map<Long, Long> accountUserMap = bots.stream()
            .collect(Collectors.toMap(bot -> bot.accountId, bot -> bot.userId));
        for (Order order : orderMapper.getActiveOrders()) {
            Long userId = accountUserMap.get(order.getAccountId());
            if (userId == null) {
                continue;
            }
            runAs(userId, () -> {
                try {
                    orderService.cancelOrder(CancelOrderDTO.builder()
                        .accountId(order.getAccountId())
                        .orderId(order.getId())
                        .build());
                } catch (Exception e) {
                    log.debug("清理遗留挂单失败：orderId={}, reason={}", order.getId(),
                        e.getMessage());
                }
            });
        }
        log.info("已清理 {} 个机器人账户的遗留挂单", accountIds.size());
    }

    /**
     * 限制委托价格
     * @param price 委托价格
     * @param quote 行情快照
     * @return 限制后价格
     */
    private BigDecimal clampPrice(BigDecimal price, StockQuoteVO quote) {
        BigDecimal base = quote.getPreClose() != null ? quote.getPreClose() : quote.getPrice();
        BigDecimal ratio = maxDeviationRatio();
        BigDecimal max = base.multiply(BigDecimal.ONE.add(ratio));
        BigDecimal min = base.multiply(BigDecimal.ONE.subtract(ratio));
        BigDecimal clamped = price.max(min).min(max).max(TICK);
        return clamped.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 获取最大偏离比例
     * @return 最大偏离比例
     */
    private BigDecimal maxDeviationRatio() {
        BigDecimal maxDeviation = properties.getMaxDeviationPercent() == null
            ? new BigDecimal("2.0") : properties.getMaxDeviationPercent();
        return maxDeviation.movePointLeft(2);
    }

    /**
     * 获取随机委托数量
     * @param random 随机数生成器
     * @return 委托数量
     */
    private int randomQuantity(ThreadLocalRandom random) {
        return ORDER_QUANTITIES[random.nextInt(ORDER_QUANTITIES.length)];
    }

    /**
     * 以指定用户身份执行操作
     * @param userId 用户id
     * @param action 操作
     */
    private void runAs(Long userId, Runnable action) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
            userId, null, Collections.emptyList()));
        SecurityContextHolder.setContext(context);
        try {
            action.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * 机器人身份
     */
    private static class Bot {
        private final Long userId;      // 用户id
        private final Long accountId;   // 资金账户id

        private Bot(Long userId, Long accountId) {
            this.userId = userId;
            this.accountId = accountId;
        }
    }

    /**
     * 待撤委托
     */
    private static class PendingOrder {
        private final Bot bot;          // 机器人
        private final Long orderId;     // 委托单id
        private final long createTime;  // 创建时间

        private PendingOrder(Bot bot, Long orderId, long createTime) {
            this.bot = bot;
            this.orderId = orderId;
            this.createTime = createTime;
        }
    }
}
