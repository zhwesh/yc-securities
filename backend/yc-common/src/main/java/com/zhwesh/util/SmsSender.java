package com.zhwesh.util;

/**
 * 短信发送接口
 * 真实项目对接短信平台（如阿里云短信/腾讯云短信，签名与模板需报备资质）；
 * 当前使用模拟实现（SmsSenderTestImpl）；
 * 后续接入真实平台时新增实现类（如 SmsSenderRealImpl），
 * 通过 @Qualifier / @Primary 等注解切换生效的 bean。
 */
public interface SmsSender {

    /**
     * 发送短信
     * @param phone 手机号
     * @param content 短信内容
     */
    void send(String phone, String content);
}
