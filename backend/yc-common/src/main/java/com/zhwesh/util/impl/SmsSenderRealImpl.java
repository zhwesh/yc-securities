package com.zhwesh.util.impl;

import com.zhwesh.exception.BusinessException;
import com.zhwesh.util.SmsSender;
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
 * 短信发送真实实现（通用 HTTP 适配器）
 *
 * 协议约定（接入具体短信平台时按厂商文档调整路径、请求头和响应字段）：
 * POST {base-url}/sms/send  {phone, content, signName}
 * 请求头：X-Access-Key、X-Timestamp、X-Request-No、X-Sign
 * 签名：HMAC-SHA256(accessKey + timestamp + requestNo + body, secretKey)，十六进制小写
 * 响应：{"code":0,"message":"ok"}，code=0 表示成功
 *
 * 未配置 sms.* 或没有真实短信资质时，当前默认仍注入 TestImpl，
 * 本类只有在 @Qualifier 切换后才会被调用。
 */
@Component("smsSenderRealImpl")
public class SmsSenderRealImpl implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(SmsSenderRealImpl.class);

    private static final String SEND_PATH = "/sms/send";

    private final String accessKey;
    private final String secretKey;
    private final String signName;
    private final RestClient restClient;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public SmsSenderRealImpl(
        @Value("${sms.base-url:}") String baseUrl,
        @Value("${sms.access-key:}") String accessKey,
        @Value("${sms.secret-key:}") String secretKey,
        @Value("${sms.sign-name:}") String signName) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.signName = signName;
        this.restClient = baseUrl == null || baseUrl.isBlank()
            ? null
            : RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public void send(String phone, String content) {
        ensureConfigured();

        String requestNo = UUID.randomUUID().toString().replace("-", "");
        String timestamp = String.valueOf(System.currentTimeMillis());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phone", phone);
        body.put("content", content);
        if (signName != null && !signName.isBlank()) {
            body.put("signName", signName);
        }
        String json = jsonMapper.writeValueAsString(body);
        String sign = hmacSha256(accessKey + timestamp + requestNo + json);

        Map<?, ?> response;
        try {
            response = restClient.post()
                .uri(SEND_PATH)
                .header("X-Access-Key", accessKey)
                .header("X-Timestamp", timestamp)
                .header("X-Request-No", requestNo)
                .header("X-Sign", sign)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .retrieve()
                .body(Map.class);
        } catch (RestClientException e) {
            log.error("短信发送调用失败：phone={}, requestNo={}", maskPhone(phone), requestNo, e);
            throw new BusinessException("短信发送服务调用失败，请稍后重试");
        }

        if (response == null || !"0".equals(String.valueOf(response.get("code")))) {
            String message = response == null
                ? "渠道无响应" : String.valueOf(response.get("message"));
            log.warn("短信发送失败：phone={}, requestNo={}, message={}",
                maskPhone(phone), requestNo, message);
            throw new BusinessException("短信发送失败：" + message);
        }
        log.info("短信发送成功（真实渠道）：phone={}, requestNo={}", maskPhone(phone), requestNo);
    }

    private void ensureConfigured() {
        if (restClient == null || accessKey == null || accessKey.isBlank()
            || secretKey == null || secretKey.isBlank()) {
            throw new BusinessException("短信渠道未配置，请设置 sms.*");
        }
    }

    private String hmacSha256(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.error("短信渠道签名失败", e);
            throw new BusinessException("短信渠道签名失败");
        }
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
