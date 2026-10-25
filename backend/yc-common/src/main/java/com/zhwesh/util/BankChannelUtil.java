package com.zhwesh.util;

/**
 * 银行渠道接口
 * 真实项目对接支付公司/银联，提供银行卡校验、协议解约等能力。
 * 当前使用模拟实现（BankChannelUtilTestImpl）；
 * 后续接入真实 API 时新增实现类（如 BankChannelUtilRealImpl），
 * 通过 @Qualifier / @Primary 等注解切换生效的 bean。
 */
public interface BankChannelUtil {

    /**
     * 银行卡与预留手机号匹配校验（卡号、预留手机号）
     * @param cardNumber 银行卡号
     * @param phone 银行预留手机号
     */
    void checkCardPhone(String cardNumber, String phone);

    /**
     * 解除银行卡绑定（协议解约）
     * @param cardNumber 银行卡号
     */
    void unbindCard(String cardNumber);
}
