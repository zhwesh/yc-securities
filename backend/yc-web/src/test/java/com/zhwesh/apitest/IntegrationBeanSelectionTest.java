package com.zhwesh.apitest;

import com.zhwesh.util.BankChannelUtil;
import com.zhwesh.util.FundingChannelUtil;
import com.zhwesh.util.SmsSender;
import com.zhwesh.util.impl.BankChannelUtilRealImpl;
import com.zhwesh.util.impl.FundingChannelUtilRealImpl;
import com.zhwesh.util.impl.SmsSenderRealImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 integration.* 配置可以切换第三方实现 Bean
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "integration.bank-channel-bean=bankChannelUtilRealImpl",
        "integration.sms-bean=smsSenderRealImpl",
        "integration.funding-bean=fundingChannelUtilRealImpl"
    })
@ActiveProfiles({"dev", "test"})
class IntegrationBeanSelectionTest {

    @Autowired
    private BankChannelUtil bankChannelUtil;

    @Autowired
    private SmsSender smsSender;

    @Autowired
    private FundingChannelUtil fundingChannelUtil;

    @Test
    void selectsRealImplsFromProperties() {
        assertThat(bankChannelUtil).isInstanceOf(BankChannelUtilRealImpl.class);
        assertThat(smsSender).isInstanceOf(SmsSenderRealImpl.class);
        assertThat(fundingChannelUtil).isInstanceOf(FundingChannelUtilRealImpl.class);
    }
}
