package com.zhwesh.util;

import java.math.BigDecimal;

/**
 * 资金渠道接口（银证转账/银行存管）
 * 真实项目对接银行存管网关：入金从绑定银行卡转入资金账户，出金转回绑定银行卡。
 * 当前使用模拟实现（FundingChannelUtilTestImpl），
 * 接入真实存管网关时使用实现类（FundingChannelUtilRealImpl），
 * 通过 @Qualifier / @Primary 在注入点切换生效 bean。
 */
public interface FundingChannelUtil {

    /**
     * 银证转账入金（银行→券商）
     * @param accountId 资金账户id（资金账号）
     * @param bankCardNumber 绑定银行卡号
     * @param amount 入金金额
     * @return 渠道流水号
     */
    String deposit(Long accountId, String bankCardNumber, BigDecimal amount);

    /**
     * 银证转账出金（券商→银行）
     * @param accountId 资金账户id（资金账号）
     * @param bankCardNumber 绑定银行卡号
     * @param amount 出金金额
     * @return 渠道流水号
     */
    String withdraw(Long accountId, String bankCardNumber, BigDecimal amount);
}
