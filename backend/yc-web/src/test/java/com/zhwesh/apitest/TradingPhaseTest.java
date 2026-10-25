package com.zhwesh.apitest;

import com.zhwesh.util.TradingTimeUtil;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 交易时段判断测试
 */
class TradingPhaseTest {

    @Test
    void phases() {
        TradingTimeUtil util = new TradingTimeUtil();
        ReflectionTestUtils.setField(util, "alwaysOn", false);
        ReflectionTestUtils.setField(util, "callAuctionEnabled", true);

        assertThat(util.getPhase(LocalDateTime.parse("2026-10-22T09:16:00")).getCode())
            .isEqualTo(1);
        assertThat(util.getPhase(LocalDateTime.parse("2026-10-22T09:20:00")).getCode())
            .isEqualTo(2);
        assertThat(util.getPhase(LocalDateTime.parse("2026-10-22T09:25:00")).getCode())
            .isEqualTo(3);
        assertThat(util.getPhase(LocalDateTime.parse("2026-10-22T09:30:00")).getCode())
            .isEqualTo(4);
        assertThat(util.getPhase(LocalDateTime.parse("2026-10-22T11:30:00")).getCode())
            .isEqualTo(4);
        assertThat(util.getPhase(LocalDateTime.parse("2026-10-22T12:00:00")).getCode())
            .isEqualTo(0);
        assertThat(util.getPhase(LocalDateTime.parse("2026-10-22T14:57:00")).getCode())
            .isEqualTo(5);
        assertThat(util.getPhase(LocalDateTime.parse("2026-10-22T15:00:00")).getCode())
            .isEqualTo(0);
        assertThat(util.getPhase(LocalDateTime.parse("2026-10-22T15:01:00")).getCode())
            .isEqualTo(0);
        assertThat(util.getPhase(LocalDateTime.parse("2026-10-24T10:00:00")).getCode())
            .isEqualTo(0);
    }
}
