package com.zhwesh.config;

import com.zhwesh.properties.IntegrationProperties;
import com.zhwesh.util.BankChannelUtil;
import com.zhwesh.util.FundingChannelUtil;
import com.zhwesh.util.SmsSender;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 第三方能力 Bean 选择器
 * 业务代码只注入接口，实际使用 TestImpl 还是 RealImpl 由 integration.* 配置决定
 */
@Configuration
public class ThirdPartyBeanConfig {

    @Bean
    @Primary
    public BankChannelUtil activeBankChannelUtil(IntegrationProperties properties,
        ApplicationContext applicationContext) {
        return resolve(applicationContext, properties.getBankChannelBean(),
            BankChannelUtil.class, "integration.bank-channel-bean");
    }

    @Bean
    @Primary
    public SmsSender activeSmsSender(IntegrationProperties properties,
        ApplicationContext applicationContext) {
        return resolve(applicationContext, properties.getSmsBean(),
            SmsSender.class, "integration.sms-bean");
    }

    @Bean
    @Primary
    public FundingChannelUtil activeFundingChannelUtil(IntegrationProperties properties,
        ApplicationContext applicationContext) {
        return resolve(applicationContext, properties.getFundingBean(),
            FundingChannelUtil.class, "integration.funding-bean");
    }

    private <T> T resolve(ApplicationContext applicationContext, String beanName,
        Class<T> type, String propertyName) {
        if (beanName == null || beanName.isBlank()) {
            throw new IllegalStateException(propertyName + " 不能为空");
        }
        if (!applicationContext.containsBean(beanName)) {
            throw new IllegalStateException(propertyName + " 指定的 Bean 不存在：" + beanName);
        }
        return applicationContext.getBean(beanName, type);
    }
}
