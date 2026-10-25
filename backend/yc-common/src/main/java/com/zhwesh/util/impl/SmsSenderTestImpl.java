package com.zhwesh.util.impl;

import com.zhwesh.util.SmsSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 短信发送模拟实现（测试用）
 * 不调用真实短信平台，仅打印日志。真实实现类接入后通过 @Qualifier 切换。
 */
@Component("smsSenderTestImpl")
public class SmsSenderTestImpl implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(SmsSenderTestImpl.class);

    @Override
    public void send(String phone, String content) {
        // 真实场景：调用短信平台 API 发送
        log.info("发送短信（模拟）：phone={}，content={}", phone, content);
    }
}
