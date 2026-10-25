package com.zhwesh.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户登录所需信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsernameLoginDTO {
    private String username;    // 用户名
    private String password;    // 密码
}
