package com.zhwesh.apitest.support;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试用 HTTP 客户端：封装全部 REST 接口
 */
public class ApiClient {

    private final RestClient restClient;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private String token;

    public ApiClient(int port) {
        this.restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    public String token() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    // ---------------- 通用请求 ----------------

    public JsonNode get(String path) {
        return exchange(HttpMethod.GET, path, null);
    }

    public JsonNode post(String path, Object body) {
        return exchange(HttpMethod.POST, path, body);
    }

    public JsonNode put(String path, Object body) {
        return exchange(HttpMethod.PUT, path, body);
    }

    public JsonNode delete(String path, Object body) {
        return exchange(HttpMethod.DELETE, path, body);
    }

    public JsonNode postMultipart(String path, String filename, byte[] content) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        try {
            String response = restClient.post().uri(path)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .exchange((request, res) -> new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8));
            return jsonMapper.readTree(response);
        } catch (Exception e) {
            throw new IllegalStateException("API multipart failed: " + path, e);
        }
    }

    private JsonNode exchange(HttpMethod method, String path, Object body) {
        try {
            RestClient.RequestBodySpec spec = restClient.method(method).uri(URI.create(path));
            if (token != null) {
                spec.header("Authorization", "Bearer " + token);
            }
            if (body != null) {
                spec.contentType(MediaType.APPLICATION_JSON).body(jsonMapper.writeValueAsString(body));
            }
            String response = spec.exchange(
                (request, res) -> new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8));
            return jsonMapper.readTree(response);
        } catch (Exception e) {
            throw new IllegalStateException("API failed: " + method + " " + path, e);
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    public static Map<String, Object> body(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }

    // ---------------- 用户 ----------------

    public JsonNode sendUserCode(String phone) {
        return get("/user/code?phone=" + enc(phone));
    }

    public JsonNode register(Map<String, Object> body) {
        return post("/user/register", body);
    }

    public JsonNode loginUsername(String username, String password) {
        JsonNode response = post("/user/login/username",
            body("username", username, "password", password));
        saveToken(response);
        return response;
    }

    public JsonNode loginPhone(String phone, String code) {
        JsonNode response = post("/user/login/phone", body("phone", phone, "code", code));
        saveToken(response);
        return response;
    }

    private void saveToken(JsonNode response) {
        if (response.path("code").asInt() == 1) {
            token = response.path("data").path("token").asString();
        }
    }

    public JsonNode currentUser() {
        return get("/user");
    }

    public JsonNode updateUser(Map<String, Object> body) {
        return put("/user/update", body);
    }

    public JsonNode uploadAvatar(byte[] png) {
        return postMultipart("/user/avatar", "avatar.png", png);
    }

    public JsonNode resetPassword(Map<String, Object> body) {
        return put("/user/password/reset", body);
    }

    // ---------------- 资金账户 ----------------

    public JsonNode accounts() {
        return get("/account");
    }

    public JsonNode openAccount(String password) {
        return post("/account/open", body("password", password));
    }

    public JsonNode openBoard(Long accountId, List<String> boards) {
        return put("/account/board/open", body("accountId", accountId, "boards", boards));
    }

    public JsonNode updateTradePassword(Long accountId, String oldPassword, String newPassword) {
        return put("/account/password/update", body(
            "accountId", accountId, "oldPassword", oldPassword, "newPassword", newPassword));
    }

    public JsonNode verifyTradePassword(Long accountId, String tradePassword, int onlineHours) {
        return post("/account/password/verify", body(
            "accountId", accountId, "tradePassword", tradePassword, "onlineHours", onlineHours));
    }

    public JsonNode deposit(Long accountId, BigDecimal amount) {
        return put("/account/deposit", body("accountId", accountId, "amount", amount));
    }

    public JsonNode withdraw(Long accountId, BigDecimal amount) {
        return put("/account/withdraw", body("accountId", accountId, "amount", amount));
    }

    // ---------------- 银行卡 ----------------

    public JsonNode bankCards(Long accountId) {
        return get("/bank-card?accountId=" + accountId);
    }

    public JsonNode bindBankCard(Long accountId, String cardNumber, String bankName,
        String phone, String code) {
        return post("/bank-card", body(
            "accountId", accountId, "cardNumber", cardNumber,
            "bankName", bankName, "phone", phone, "code", code));
    }

    public JsonNode unbindBankCard(Long accountId, String cardNumber, String phone, String code) {
        return delete("/bank-card", body(
            "accountId", accountId, "cardNumber", cardNumber, "phone", phone, "code", code));
    }

    public JsonNode sendBankCardCode(String phone) {
        return get("/bank-card/code?phone=" + enc(phone));
    }

    public JsonNode verifyBankCard(String cardNumber, String phone) {
        return post("/bank-card/verify", body("cardNumber", cardNumber, "phone", phone));
    }

    // ---------------- 股票 ----------------

    public JsonNode stocks(String keyword, int pageNum, int pageSize) {
        return get("/stock?keyword=" + enc(keyword) + "&pageNum=" + pageNum + "&pageSize=" + pageSize);
    }

    public JsonNode stockDetail(String code) {
        return get("/stock/" + code);
    }

    // ---------------- 行情 ----------------

    public JsonNode quote(String code) {
        return get("/market/quote?code=" + code);
    }

    public JsonNode quotes(String codes) {
        return get("/market/quotes?codes=" + codes);
    }

    public JsonNode orderBook(String code, int depth) {
        return get("/market/order-book?code=" + code + "&depth=" + depth);
    }

    public JsonNode rank(int sortBy, int order, int pageNum, int pageSize) {
        return get("/market/rank?sortBy=" + sortBy + "&order=" + order
            + "&pageNum=" + pageNum + "&pageSize=" + pageSize);
    }

    public JsonNode minute(String code, String date) {
        String path = "/market/minute?code=" + code;
        if (date != null) {
            path += "&date=" + date;
        }
        return get(path);
    }

    public JsonNode minuteKLine(String code, int period, String endTime, int limit) {
        String path = "/market/minute-kline?code=" + code + "&period=" + period
            + "&limit=" + limit;
        if (endTime != null) {
            path += "&endTime=" + enc(endTime);
        }
        return get(path);
    }

    public JsonNode marketTrades(String code, int limit) {
        return get("/market/trades?code=" + code + "&limit=" + limit);
    }

    public JsonNode indexes() {
        return get("/market/indexes");
    }

    public JsonNode auction(String code) {
        return get("/market/auction?code=" + code);
    }

    public JsonNode kline(String code, int period, int adjust, String endDate, int limit) {
        String path = "/market/kline?code=" + code + "&period=" + period
            + "&adjust=" + adjust + "&limit=" + limit;
        if (endDate != null) {
            path += "&endDate=" + endDate;
        }
        return get(path);
    }

    // ---------------- 自选股 ----------------

    public JsonNode watchlist() {
        return get("/watchlist");
    }

    public JsonNode addWatchlist(String code) {
        return post("/watchlist", body("code", code));
    }

    public JsonNode deleteWatchlist(String code) {
        return delete("/watchlist?code=" + enc(code), null);
    }

    public JsonNode updateWatchlist(List<String> codes) {
        return put("/watchlist", body("codes", codes));
    }

    // ---------------- 委托/成交/持仓 ----------------

    public JsonNode createOrder(Map<String, Object> body) {
        return post("/order", body);
    }

    public JsonNode orders(Long accountId, String code, Integer status, int pageNum, int pageSize) {
        String path = "/order?accountId=" + accountId + "&pageNum=" + pageNum + "&pageSize=" + pageSize;
        if (code != null) {
            path += "&code=" + code;
        }
        if (status != null) {
            path += "&status=" + status;
        }
        return get(path);
    }

    public JsonNode orderDetail(Long id) {
        return get("/order/" + id);
    }

    public JsonNode cancelOrder(Long accountId, Long orderId) {
        return put("/order/cancel", body("accountId", accountId, "orderId", orderId));
    }

    public JsonNode trades(Long accountId, String code, int pageNum, int pageSize) {
        String path = "/trade?accountId=" + accountId + "&pageNum=" + pageNum + "&pageSize=" + pageSize;
        if (code != null) {
            path += "&code=" + code;
        }
        return get(path);
    }

    public JsonNode positions(Long accountId) {
        return get("/position?accountId=" + accountId);
    }
}
