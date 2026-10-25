package com.zhwesh.apitest;

import com.zhwesh.exception.BusinessException;
import com.zhwesh.util.impl.BankChannelUtilRealImpl;
import com.zhwesh.util.impl.SmsSenderRealImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 真实第三方实现未配置时的保护测试
 */
class RealImplConfigTest {

    @Test
    void bankChannelRequiresConfig() {
        BankChannelUtilRealImpl bankChannel = new BankChannelUtilRealImpl("", "", "");
        assertThatThrownBy(() -> bankChannel.checkCardPhone("6228480402564890018", "13800138000"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("未配置");
    }

    @Test
    void smsSenderRequiresConfig() {
        SmsSenderRealImpl smsSender = new SmsSenderRealImpl("", "", "", "");
        assertThatThrownBy(() -> smsSender.send("13800138000", "test"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("未配置");
    }
}
