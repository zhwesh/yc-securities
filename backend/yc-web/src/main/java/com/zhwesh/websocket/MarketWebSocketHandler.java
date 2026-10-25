package com.zhwesh.websocket;

import com.zhwesh.VO.OrderBookVO;
import com.zhwesh.VO.OrderVO;
import com.zhwesh.VO.StockQuoteVO;
import com.zhwesh.VO.StockTradeDetailVO;
import com.zhwesh.engine.market.MarketEngine;
import com.zhwesh.engine.match.MatchEngine;
import com.zhwesh.entity.Account;
import com.zhwesh.mapper.AccountMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 行情 WebSocket 推送
 * 连接：/ws?token=JWT
 * 客户端消息：{"type":"subscribe","codes":["600000"]} / {"type":"unsubscribe","codes":[...]} / {"type":"ping"}
 * 服务端消息：quote / orderBook（每秒节流）、trade（实时）、orderUpdate（实时）、pong
 */
@Component
public class MarketWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MarketWebSocketHandler.class);

    /**
     * 单连接订阅股票上限
     */
    private static final int MAX_SUBSCRIBE_CODES = 50;

    /**
     * 盘口推送档位
     */
    private static final int ORDER_BOOK_DEPTH = 5;

    private final MarketEngine marketEngine;
    private final MatchEngine matchEngine;
    private final AccountMapper accountMapper;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    public MarketWebSocketHandler(MarketEngine marketEngine, MatchEngine matchEngine,
        AccountMapper accountMapper) {
        this.marketEngine = marketEngine;
        this.matchEngine = matchEngine;
        this.accountMapper = accountMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        Set<Long> accountIds = new HashSet<>();
        List<Account> accounts = accountMapper.getByUserId(userId);
        if (accounts != null) {
            for (Account account : accounts) {
                accountIds.add(account.getId());
            }
        }
        sessions.put(session.getId(), new SessionInfo(session, accountIds));
        log.info("WebSocket 连接建立：sessionId={}, userId={}", session.getId(), userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        SessionInfo info = sessions.get(session.getId());
        if (info == null) {
            return;
        }
        try {
            Map<?, ?> payload = jsonMapper.readValue(message.getPayload(), Map.class);
            String type = payload.get("type") == null ? "" : String.valueOf(payload.get("type"));
            switch (type) {
                case "subscribe" -> subscribe(info, payload);
                case "unsubscribe" -> unsubscribe(info, payload);
                case "ping" -> send(session, message("pong"));
                default -> {
                }
            }
        } catch (Exception e) {
            log.warn("WebSocket 消息处理失败：sessionId={}", session.getId(), e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    /**
     * 每秒推送订阅股票的行情与盘口（节流）
     */
    @Scheduled(fixedRate = 1000)
    public void pushQuotesAndBooks() {
        for (SessionInfo info : sessions.values()) {
            for (String code : info.subscribedCodes) {
                pushQuote(info, code);
                pushOrderBook(info, code);
            }
        }
    }

    /**
     * 推送实时成交
     * @param code 股票代码
     * @param trade 成交明细
     */
    public void pushTrade(String code, StockTradeDetailVO trade) {
        Map<String, Object> payload = message("trade");
        payload.put("code", code);
        payload.put("data", trade);
        broadcastByCode(code, payload);
    }

    /**
     * 推送委托状态更新（仅订单所属账户的连接）
     * @param accountId 资金账户id
     * @param order 委托单
     */
    public void pushOrderUpdate(Long accountId, OrderVO order) {
        if (accountId == null || order == null) {
            return;
        }
        Map<String, Object> payload = message("orderUpdate");
        payload.put("data", order);
        for (SessionInfo info : sessions.values()) {
            if (info.accountIds.contains(accountId)) {
                send(info.session, payload);
            }
        }
    }

    /**
     * 订阅股票（上限 50）
     */
    private void subscribe(SessionInfo info, Map<?, ?> payload) {
        List<String> codes = extractCodes(payload);
        for (String code : codes) {
            if (info.subscribedCodes.size() >= MAX_SUBSCRIBE_CODES) {
                break;
            }
            info.subscribedCodes.add(code);
        }
        for (String code : codes) {
            if (info.subscribedCodes.contains(code)) {
                pushQuote(info, code);
                pushOrderBook(info, code);
            }
        }
    }

    /**
     * 取消订阅
     */
    private void unsubscribe(SessionInfo info, Map<?, ?> payload) {
        info.subscribedCodes.removeAll(extractCodes(payload));
    }

    /**
     * 提取消息中的股票代码列表
     */
    private List<String> extractCodes(Map<?, ?> payload) {
        List<String> codes = new ArrayList<>();
        Object raw = payload.get("codes");
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    codes.add(String.valueOf(item));
                }
            }
        }
        return codes;
    }

    private void pushQuote(SessionInfo info, String code) {
        StockQuoteVO quote = marketEngine.getQuote(code);
        if (quote == null) {
            return;
        }
        Map<String, Object> payload = message("quote");
        payload.put("data", quote);
        send(info.session, payload);
    }

    private void pushOrderBook(SessionInfo info, String code) {
        if (marketEngine.getQuote(code) == null) {
            return;
        }
        OrderBookVO book = matchEngine.getSnapshot(code, ORDER_BOOK_DEPTH);
        Map<String, Object> payload = message("orderBook");
        payload.put("code", code);
        payload.put("data", book);
        send(info.session, payload);
    }

    private void broadcastByCode(String code, Map<String, Object> payload) {
        for (SessionInfo info : sessions.values()) {
            if (info.subscribedCodes.contains(code)) {
                send(info.session, payload);
            }
        }
    }

    private Map<String, Object> message(String type) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        return payload;
    }

    private void send(WebSocketSession session, Map<String, Object> payload) {
        try {
            String json = jsonMapper.writeValueAsString(payload);
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (Exception e) {
            log.warn("WebSocket 推送失败：sessionId={}", session.getId(), e);
            closeQuietly(session, CloseStatus.SERVER_ERROR);
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (Exception ignored) {
        }
        sessions.remove(session.getId());
    }

    /**
     * 单连接信息
     */
    private static class SessionInfo {
        private final WebSocketSession session;
        private final Set<Long> accountIds;
        private final Set<String> subscribedCodes = ConcurrentHashMap.newKeySet();

        private SessionInfo(WebSocketSession session, Set<Long> accountIds) {
            this.session = session;
            this.accountIds = accountIds;
        }
    }
}
