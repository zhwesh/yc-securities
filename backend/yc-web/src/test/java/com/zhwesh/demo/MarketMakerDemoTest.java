package com.zhwesh.demo;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 做市机器人演示启动器
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles({"dev", "demo"})
class MarketMakerDemoTest {

    private static final Logger log = LoggerFactory.getLogger(MarketMakerDemoTest.class);

    @Value("${demo.market-maker.code:600000}")
    private String code;

    @Value("${demo.market-maker.bots:50}")
    private int bots;

    /**
     * 运行做市机器人演示
     */
    @Test
    void runUntilManuallyStopped() throws InterruptedException {
        log.info("做市机器人演示已启动：端口8080，做市股票{}，机器人数量{}", code, bots);
        while (true) {
            Thread.sleep(30_000L);
            log.info("做市机器人演示运行中……");
        }
    }
}
