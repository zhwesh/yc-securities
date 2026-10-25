package com.zhwesh.util.impl;

import com.zhwesh.exception.BusinessException;
import com.zhwesh.util.BankChannelUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 银行渠道模拟实现（测试用）
 * 不调用真实支付通道，卡号仅做 Luhn 校验 + 长度/格式校验，解约直接成功。
 * 真实实现类接入后，在各注入点通过 @Qualifier("bankChannelUtilTestImpl" / "bankChannelUtilRealImpl") 切换。
 */
@Component("bankChannelUtilTestImpl")
public class BankChannelUtilTestImpl implements BankChannelUtil {

    private static final Logger log = LoggerFactory.getLogger(BankChannelUtilTestImpl.class);

    @Override
    public void checkCardPhone(String cardNumber, String phone) {
        if (cardNumber == null || !cardNumber.matches("\\d{16,19}") || !luhnCheck(cardNumber)) {
            throw new BusinessException("银行卡验证失败：银行卡号不合法");
        }
        if (phone == null || !phone.matches("1\\d{10}")) {
            throw new BusinessException("银行卡验证失败：预留手机号不合法");
        }

        // 真实场景：调用支付公司卡号与预留手机号校验接口
        log.info("银行卡与预留手机号匹配校验通过（模拟）：cardNumber={}，phone={}", cardNumber, phone);
    }

    @Override
    public void unbindCard(String cardNumber) {
        // 真实场景：调用支付公司协议解约接口
        log.info("银行卡协议解约成功（模拟）：cardNumber={}", cardNumber);
    }

    /**
     * Luhn 算法校验银行卡号
     */
    private boolean luhnCheck(String cardNumber) {
        int sum = 0;
        boolean doubleDigit = false;
        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            int digit = cardNumber.charAt(i) - '0';
            if (doubleDigit) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }
}
