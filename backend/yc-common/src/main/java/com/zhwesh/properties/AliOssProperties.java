package com.zhwesh.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云OSS相关配置
 */
@Component
@ConfigurationProperties(prefix = "alioss")
@Data
public class AliOssProperties {
    private String endpoint;         // 服务地址
    private String bucketName;       // 存储空间名称
    private String region;           // 地域
    private String accessKeyId;      // 访问密钥id
    private String accessKeySecret;  // 访问密钥密码
}
