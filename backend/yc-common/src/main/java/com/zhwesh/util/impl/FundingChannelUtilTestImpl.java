package com.zhwesh.util.impl;

import com.zhwesh.exception.BusinessException;
import com.zhwesh.util.FundingChannelUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 资金渠道模拟实现（当前生效）
 * 模拟银证转账成功并返回模拟流水号。
 * 真实实现见 FundingChannelUtilRealImpl，接入存管网关后
 * 在注入点通过 @Qualifier("fundingChannelUtilRealImpl") 切换。
 */
@Component("fundingChannelUtilTestImpl")
public class FundingChannelUtilTestImpl implements FundingChannelUtil {

    private static final Logger log = LoggerFactory.getLogger(FundingChannelUtilTestImpl.class);

    @Override
    public String deposit(Long accountId, String bankCardNumber, BigDecimal amount) {
        validate(accountId, bankCardNumber, amount);
        String tradeNo = "TEST-IN-" + UUID.randomUUID().toString().replace("-", "");
        log.info("银证转账入金成功（模拟）：accountId={}, card={}, amount={}, tradeNo={}",
            accountId, mask(bankCardNumber), amount, tradeNo);
        return tradeNo;
    }

    @Override
    public String withdraw(Long accountId, String bankCardNumber, BigDecimal amount) {
        validate(accountId, bankCardNumber, amount);
        String tradeNo = "TEST-OUT-" + UUID.randomUUID().toString().replace("-", "");
        log.info("银证转账出金成功（模拟）：accountId={}, card={}, amount={}, tradeNo={}",
            accountId, mask(bankCardNumber), amount, tradeNo);
        return tradeNo;
    }

    /**
     * 基础参数校验
     */
    private void validate(Long accountId, String bankCardNumber, BigDecimal amount) {
        if (accountId == null) {
            throw new BusinessException("资金账户id不能为空");
        }
        if (bankCardNumber == null || !bankCardNumber.matches("\\d{16,19}")) {
            throw new BusinessException("银行卡号不合法");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("转账金额必须大于0");
        }
    }

    /**
     * 卡号脱敏
     */
    private String mask(String bankCardNumber) {
        return bankCardNumber.substring(0, 4) + "****"
            + bankCardNumber.substring(bankCardNumber.length() - 4);
    }
}
