package com.zhwesh.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 手机验证码登录所需信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhoneLoginDTO {
    private String phone;   // 手机号
    private String code;    // 短信验证码
}
