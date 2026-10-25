package com.zhwesh.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 第三方能力实现选择配置
 * 值填对应实现类的 Spring Bean 名称，例如：
 * bankChannelUtilTestImpl / bankChannelUtilRealImpl
 * smsSenderTestImpl / smsSenderRealImpl
 * fundingChannelUtilTestImpl / fundingChannelUtilRealImpl
 */
@Data
@Component
@ConfigurationProperties(prefix = "integration")
public class IntegrationProperties {

    /**
     * 银行卡验证实现 Bean 名
     */
    private String bankChannelBean = "bankChannelUtilTestImpl";

    /**
     * 短信发送实现 Bean 名
     */
    private String smsBean = "smsSenderTestImpl";

    /**
     * 银证转账实现 Bean 名
     */
    private String fundingBean = "fundingChannelUtilTestImpl";
}
