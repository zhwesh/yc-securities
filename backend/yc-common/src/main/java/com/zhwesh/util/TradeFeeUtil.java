package com.zhwesh.util;

import com.zhwesh.properties.TradeFeeProperties;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 交易费用计算工具
 * 买入：佣金 + 过户费；卖出：佣金 + 印花税 + 过户费。
 * 费用按成交金额比例计算，保留2位小数。
 */
public class TradeFeeUtil {

    private TradeFeeUtil() {
    }

    /**
     * 买入费用
     * @param amount 成交金额
     * @param fee 费用配置
     * @return 买入费用
     */
    public static BigDecimal buyFee(BigDecimal amount, TradeFeeProperties fee) {
        if (amount == null || amount.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(fee.getCommissionRate().add(fee.getTransferFeeRate()))
            .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 卖出费用（含印花税）
     * @param amount 成交金额
     * @param fee 费用配置
     * @return 卖出费用
     */
    public static BigDecimal sellFee(BigDecimal amount, TradeFeeProperties fee) {
        if (amount == null || amount.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(
                fee.getCommissionRate().add(fee.getStampDutyRate()).add(fee.getTransferFeeRate()))
            .setScale(2, RoundingMode.HALF_UP);
    }
}
