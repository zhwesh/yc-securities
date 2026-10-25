package com.zhwesh.util;

import com.zhwesh.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 短信验证码工具类
 * 负责验证码的生成、Redis 存取与校验；短信发送委托给 SmsSender（模拟/真实实现可切换）
 */
@Component
public class SmsCodeUtil {

    /**
     * 验证码在Redis中的key前缀
     */
    private static final String CODE_KEY_PREFIX = "sms:code:";

    /**
     * 验证码有效期（秒）
     */
    private static final long CODE_TTL_SECONDS = 300;

    private final RedisUtil redisUtil;
    private final SmsSender smsSender;

    @Autowired
    public SmsCodeUtil(RedisUtil redisUtil, SmsSender smsSender) {
        this.redisUtil = redisUtil;
        this.smsSender = smsSender;
    }

    /**
     * 发送验证码
     */
    public void sendCode(String phone) {
        String code = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));
        redisUtil.setEx(CODE_KEY_PREFIX + phone, code, CODE_TTL_SECONDS);
        smsSender.send(phone, "验证码：" + code);
    }

    /**
     * 校验验证码，校验通过后立即失效
     */
    public void verifyCode(String phone, String code) {
        String key = CODE_KEY_PREFIX + phone;
        String savedCode = redisUtil.get(key);
        if (savedCode == null || !savedCode.equals(code)) {
            throw new BusinessException("验证码错误或已过期");
        }
        redisUtil.delete(key);
    }
}
