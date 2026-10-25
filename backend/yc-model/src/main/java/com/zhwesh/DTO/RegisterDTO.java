package com.zhwesh.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户注册所需信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterDTO {
    private String phone;       // 手机号
    private String code;        // 短信验证码
    private String username;    // 用户名
    private String password;    // 登录密码
    private String name;        // 姓名
    private String idNumber;    // 身份证号
}
