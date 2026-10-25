package com.zhwesh.apitest;

import com.zhwesh.apitest.support.ApiClient;
import com.zhwesh.apitest.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WebSocket 行情推送测试
 */
class WebSocketTest extends ApiTestBase {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void connectRejectsMissingToken() {
        StandardWebSocketClient client = new StandardWebSocketClient();
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        assertThatThrownBy(() -> client.execute(new CollectingHandler(messages),
            "ws://localhost:" + port + "/ws").get(5, TimeUnit.SECONDS))
            .isInstanceOf(Exception.class);
    }

    @Test
    void subscribeAndPing() throws Exception {
        StandardWebSocketClient client = new StandardWebSocketClient();
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        WebSocketSession session = client.execute(new CollectingHandler(messages),
            "ws://localhost:" + port + "/ws?token=" + api.token()).get(5, TimeUnit.SECONDS);
        try {
            session.sendMessage(new TextMessage("{\"type\":\"ping\"}"));
            JsonNode pong = waitForType(messages, "pong", 5000);
            assertThat(pong.path("type").asString()).isEqualTo("pong");

            session.sendMessage(new TextMessage("{\"type\":\"subscribe\",\"codes\":[\"600000\"]}"));
            JsonNode quote = waitForType(messages, "quote", 5000);
            assertThat(quote.path("data").path("code").asString()).isEqualTo("600000");
            assertThat(quote.path("data").path("price").asDouble()).isGreaterThan(0);
        } finally {
            session.close();
        }
    }

    @Test
    void orderUpdateAndTradePush() throws Exception {
        StandardWebSocketClient client = new StandardWebSocketClient();
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        WebSocketSession session = client.execute(new CollectingHandler(messages),
            "ws://localhost:" + port + "/ws?token=" + api.token()).get(5, TimeUnit.SECONDS);
        try {
            session.sendMessage(new TextMessage("{\"type\":\"subscribe\",\"codes\":[\"000001\"]}"));
            waitForType(messages, "quote", 5000);

            JsonNode sell = api.createOrder(ApiClient.body(
                "accountId", 1, "code", "000001", "type", 1,
                "price", new BigDecimal("11.50"), "quantity", -100, "orderKey", "200001"));
            assertThat(sell.path("code").asInt()).isEqualTo(1);
            JsonNode orderUpdate = waitForType(messages, "orderUpdate", 5000);
            assertThat(orderUpdate.path("data").path("stockCode").asString()).isEqualTo("000001");

            JsonNode buy = api.createOrder(ApiClient.body(
                "accountId", 1, "code", "000001", "type", 1,
                "price", new BigDecimal("11.50"), "quantity", 100, "orderKey", "200002"));
            assertThat(buy.path("code").asInt()).isEqualTo(1);
            assertThat(buy.path("data").path("status").asInt()).isEqualTo(3);

            JsonNode trade = waitForType(messages, "trade", 5000);
            assertThat(trade.path("code").asString()).isEqualTo("000001");
            assertThat(trade.path("data").path("price").asDouble()).isCloseTo(11.50, within(0.001));
            assertThat(trade.path("data").path("volume").asLong()).isEqualTo(100);
            assertThat(trade.path("data").path("direction").asInt()).isZero();
        } finally {
            session.close();
        }
    }

    @Test
    void subscribeLimitIsFifty() throws Exception {
        List<String> codes = jdbcTemplate.queryForList(
            "SELECT code FROM stock ORDER BY code LIMIT 51", String.class);
        StandardWebSocketClient client = new StandardWebSocketClient();
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        WebSocketSession session = client.execute(new CollectingHandler(messages),
            "ws://localhost:" + port + "/ws?token=" + api.token()).get(5, TimeUnit.SECONDS);
        try {
            session.sendMessage(new TextMessage("{\"type\":\"subscribe\",\"codes\":"
                + jsonMapper.writeValueAsString(codes) + "}"));
            Thread.sleep(2500);
            Set<String> quoted = new HashSet<>();
            String message;
            while ((message = messages.poll()) != null) {
                JsonNode node = jsonMapper.readTree(message);
                if ("quote".equals(node.path("type").asString())) {
                    quoted.add(node.path("data").path("code").asString());
                }
            }
            assertThat(quoted).isNotEmpty();
            assertThat(quoted.size()).isLessThanOrEqualTo(50);
        } finally {
            session.close();
        }
    }

    private JsonNode waitForType(BlockingQueue<String> messages, String type, long timeoutMs)
        throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            long wait = Math.max(1, deadline - System.currentTimeMillis());
            String message = messages.poll(wait, TimeUnit.MILLISECONDS);
            if (message == null) {
                break;
            }
            JsonNode node = jsonMapper.readTree(message);
            if (type.equals(node.path("type").asString())) {
                return node;
            }
        }
        throw new AssertionError("未收到 WebSocket 消息：" + type);
    }

    private static class CollectingHandler extends TextWebSocketHandler {
        private final BlockingQueue<String> messages;

        private CollectingHandler(BlockingQueue<String> messages) {
            this.messages = messages;
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            messages.add(message.getPayload());
        }
    }
}
