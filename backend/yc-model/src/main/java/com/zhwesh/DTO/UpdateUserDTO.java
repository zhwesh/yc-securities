package com.zhwesh.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 修改用户信息所需信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserDTO {
    private String username;    // 新用户名
    private String image;       // 新头像URL
    private String phone;       // 新手机号
    private String code;        // 改变手机号时所需的短信验证码
}
