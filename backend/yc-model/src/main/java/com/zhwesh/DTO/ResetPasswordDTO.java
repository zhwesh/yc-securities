package com.zhwesh.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 重置密码所需信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordDTO {
    private String phone;       // 手机号
    private String code;        // 短信验证码
    private String newPassword; // 新密码
}
