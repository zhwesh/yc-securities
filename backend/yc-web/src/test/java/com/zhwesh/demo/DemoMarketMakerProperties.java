package com.zhwesh.demo;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 演示做市机器人配置
 */
@Data
@Component
@Profile("demo")
@ConfigurationProperties(prefix = "demo.market-maker")
public class DemoMarketMakerProperties {

    /**
     * 是否启用
     */
    private boolean enabled = false;

    /**
     * 做市标的代码
     */
    private String code = "600000";

    /**
     * 机器人数量
     */
    private int bots = 10;

    /**
     * 调度间隔，单位毫秒
     */
    private long intervalMs = 500;

    /**
     * 价格相对昨收的最大偏离百分比
     */
    private BigDecimal maxDeviationPercent = new BigDecimal("2.0");
}
