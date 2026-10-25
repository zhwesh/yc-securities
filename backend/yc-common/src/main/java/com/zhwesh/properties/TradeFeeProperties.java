package com.zhwesh.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 交易费用配置（佣金/印花税/过户费）
 */
@Data
@Component
@ConfigurationProperties(prefix = "trading.fee")
public class TradeFeeProperties {

    /**
     * 佣金费率（买卖双向）
     */
    private BigDecimal commissionRate = new BigDecimal("0.00025");

    /**
     * 印花税率（仅卖出）
     */
    private BigDecimal stampDutyRate = new BigDecimal("0.0005");

    /**
     * 过户费率（买卖双向）
     */
    private BigDecimal transferFeeRate = new BigDecimal("0.00001");
}
