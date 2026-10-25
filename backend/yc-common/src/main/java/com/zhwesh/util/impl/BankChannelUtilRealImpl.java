package com.zhwesh.util.impl;

import com.zhwesh.exception.BusinessException;
import com.zhwesh.util.BankChannelUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 银行卡验证真实实现（通用 HTTP 适配器）
 *
 * 协议约定（接入具体银行/支付公司时按厂商文档调整路径、请求头和响应字段）：
 * POST {base-url}/bank-card/verify  {cardNumber, phone}
 * POST {base-url}/bank-card/unbind  {cardNumber}
 * 请求头：X-App-Id、X-Timestamp、X-Request-No、X-Sign
 * 签名：HMAC-SHA256(appId + timestamp + requestNo + body, secretKey)，十六进制小写
 * 响应：{"code":0,"message":"ok"}，code=0 表示成功
 *
 * 未配置 bank-channel.card.* 或没有真实厂商资质时，当前默认仍注入 TestImpl，
 * 本类只有在 @Qualifier 切换后才会被调用。
 */
@Component("bankChannelUtilRealImpl")
public class BankChannelUtilRealImpl implements BankChannelUtil {

    private static final Logger log = LoggerFactory.getLogger(BankChannelUtilRealImpl.class);

    private static final String VERIFY_PATH = "/bank-card/verify";
    private static final String UNBIND_PATH = "/bank-card/unbind";

    private final String appId;
    private final String secretKey;
    private final RestClient restClient;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public BankChannelUtilRealImpl(
        @Value("${bank-channel.card.base-url:}") String baseUrl,
        @Value("${bank-channel.card.app-id:}") String appId,
        @Value("${bank-channel.card.secret-key:}") String secretKey) {
        this.appId = appId;
        this.secretKey = secretKey;
        this.restClient = baseUrl == null || baseUrl.isBlank()
            ? null
            : RestClient.builder().baseUrl(baseUrl).build();
    }

    /**
     * 校验卡号与银行预留手机号是否匹配
     */
    @Override
    public void checkCardPhone(String cardNumber, String phone) {
        ensureConfigured();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cardNumber", cardNumber);
        body.put("phone", phone);
        post(VERIFY_PATH, body, "银行卡验证");
        log.info("银行卡与预留手机号校验通过（真实渠道）：cardNumber={}，phone={}",
            maskCard(cardNumber), maskPhone(phone));
    }

    /**
     * 解除银行卡协议绑定
     */
    @Override
    public void unbindCard(String cardNumber) {
        ensureConfigured();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cardNumber", cardNumber);
        post(UNBIND_PATH, body, "银行卡解绑");
        log.info("银行卡协议解约成功（真实渠道）：cardNumber={}", maskCard(cardNumber));
    }

    private void post(String path, Map<String, Object> body, String action) {
        String requestNo = UUID.randomUUID().toString().replace("-", "");
        String timestamp = String.valueOf(System.currentTimeMillis());
        String json = jsonMapper.writeValueAsString(body);
        String sign = hmacSha256(appId + timestamp + requestNo + json);

        Map<?, ?> response;
        try {
            response = restClient.post()
                .uri(path)
                .header("X-App-Id", appId)
                .header("X-Timestamp", timestamp)
                .header("X-Request-No", requestNo)
                .header("X-Sign", sign)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .retrieve()
                .body(Map.class);
        } catch (RestClientException e) {
            log.error("{}调用失败：requestNo={}", action, requestNo, e);
            throw new BusinessException(action + "服务调用失败，请稍后重试");
        }

        if (response == null || !"0".equals(String.valueOf(response.get("code")))) {
            String message = response == null
                ? "渠道无响应" : String.valueOf(response.get("message"));
            log.warn("{}失败：requestNo={}, message={}", action, requestNo, message);
            throw new BusinessException(action + "失败：" + message);
        }
    }

    private void ensureConfigured() {
        if (restClient == null || appId == null || appId.isBlank()
            || secretKey == null || secretKey.isBlank()) {
            throw new BusinessException("银行卡渠道未配置，请设置 bank-channel.card.*");
        }
    }

    private String hmacSha256(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.error("银行卡渠道签名失败", e);
            throw new BusinessException("银行卡渠道签名失败");
        }
    }

    private String maskCard(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 8) {
            return cardNumber;
        }
        return cardNumber.substring(0, 4) + "****"
            + cardNumber.substring(cardNumber.length() - 4);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
