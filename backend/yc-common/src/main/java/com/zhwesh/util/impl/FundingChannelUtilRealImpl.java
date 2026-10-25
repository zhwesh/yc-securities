package com.zhwesh.util.impl;

import com.zhwesh.exception.BusinessException;
import com.zhwesh.util.FundingChannelUtil;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 资金渠道真实实现（银证转账/银行存管网关）
 * 入金：从绑定银行卡转入资金账户；出金：从资金账户转回绑定银行卡。
 * 需要存管网关商户资质，配置项（环境变量注入，禁止写入仓库）：
 * bank-channel.funding.base-url / merchant-id / secret-key。
 *
 * 网关协议约定：
 * POST {base-url}/funding/deposit、{base-url}/funding/withdraw
 * 请求头：X-Merchant-Id、X-Timestamp、X-Request-No、X-Sign
 * 签名：HMAC-SHA256(merchantId + timestamp + requestNo + body, secretKey)，十六进制小写
 * 响应：{"code":0,"message":"ok","tradeNo":"..."}，code=0 表示成功
 *
 * 接入真实网关时，在注入点通过 @Qualifier("fundingChannelUtilRealImpl") 切换生效（当前为 TestImpl）。
 */
@Component("fundingChannelUtilRealImpl")
public class FundingChannelUtilRealImpl implements FundingChannelUtil {

    private static final Logger log = LoggerFactory.getLogger(FundingChannelUtilRealImpl.class);

    /**
     * 入金路径
     */
    private static final String DEPOSIT_PATH = "/funding/deposit";

    /**
     * 出金路径
     */
    private static final String WITHDRAW_PATH = "/funding/withdraw";

    private final String merchantId;
    private final String secretKey;
    private final RestClient restClient;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public FundingChannelUtilRealImpl(
        @Value("${bank-channel.funding.base-url:}") String baseUrl,
        @Value("${bank-channel.funding.merchant-id:}") String merchantId,
        @Value("${bank-channel.funding.secret-key:}") String secretKey) {
        this.merchantId = merchantId;
        this.secretKey = secretKey;
        this.restClient = baseUrl == null || baseUrl.isBlank()
            ? null
            : RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public String deposit(Long accountId, String bankCardNumber, BigDecimal amount) {
        return transfer(DEPOSIT_PATH, accountId, bankCardNumber, amount, "入金");
    }

    @Override
    public String withdraw(Long accountId, String bankCardNumber, BigDecimal amount) {
        return transfer(WITHDRAW_PATH, accountId, bankCardNumber, amount, "出金");
    }

    /**
     * 调用存管网关发起转账
     * @param path 接口路径
     * @param accountId 资金账户id（资金账号）
     * @param bankCardNumber 绑定银行卡号
     * @param amount 转账金额
     * @param action 操作名称（日志用）
     * @return 渠道流水号
     */
    private String transfer(String path, Long accountId, String bankCardNumber,
        BigDecimal amount, String action) {
        ensureConfigured();

        String requestNo = UUID.randomUUID().toString().replace("-", "");
        String timestamp = String.valueOf(System.currentTimeMillis());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("merchantId", merchantId);
        body.put("accountNo", String.valueOf(accountId));
        body.put("bankCardNo", bankCardNumber);
        body.put("amount", amount.setScale(2, RoundingMode.HALF_UP).toPlainString());
        body.put("requestNo", requestNo);
        body.put("timestamp", timestamp);
        String json = jsonMapper.writeValueAsString(body);
        String sign = hmacSha256(merchantId + timestamp + requestNo + json);

        Map<?, ?> response;
        try {
            response = restClient.post()
                .uri(path)
                .header("X-Merchant-Id", merchantId)
                .header("X-Timestamp", timestamp)
                .header("X-Request-No", requestNo)
                .header("X-Sign", sign)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .retrieve()
                .body(Map.class);
        } catch (RestClientException e) {
            log.error("银证转账{}调用失败：accountId={}, requestNo={}", action, accountId, requestNo, e);
            throw new BusinessException("资金渠道调用失败，请稍后重试");
        }

        if (response == null || !"0".equals(String.valueOf(response.get("code")))) {
            String message = response == null
                ? "渠道无响应" : String.valueOf(response.get("message"));
            log.warn("银证转账{}失败：accountId={}, requestNo={}, message={}",
                action, accountId, requestNo, message);
            throw new BusinessException("资金渠道处理失败：" + message);
        }

        String tradeNo = response.get("tradeNo") == null
            ? "" : String.valueOf(response.get("tradeNo"));
        log.info("银证转账{}成功：accountId={}, amount={}, tradeNo={}",
            action, accountId, amount, tradeNo);
        return tradeNo;
    }

    /**
     * 校验渠道配置
     */
    private void ensureConfigured() {
        if (restClient == null || merchantId == null || merchantId.isBlank()
            || secretKey == null || secretKey.isBlank()) {
            throw new BusinessException("资金渠道未配置，请设置 bank-channel.funding.* 配置");
        }
    }

    /**
     * HMAC-SHA256 签名（十六进制小写）
     */
    private String hmacSha256(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.error("资金渠道签名失败", e);
            throw new BusinessException("资金渠道签名失败");
        }
    }
}
