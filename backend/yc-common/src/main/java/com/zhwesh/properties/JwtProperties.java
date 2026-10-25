package com.zhwesh.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * jwt令牌相关配置
 */
@Component
@ConfigurationProperties(prefix = "jwt")
@Data
public class JwtProperties {
    private String secretKey;  // 签名密钥（长度至少32字节）
    private long ttl;          // 令牌有效期（毫秒）
}
