package com.zhwesh.VO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 登录返回信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO {
    private String token;               // JWT令牌
    private Long id;                    // 用户id
    private String username;            // 用户名
    private String name;                // 姓名
    private String image;               // 头像URL
    private String phone;               // 手机号
    private LocalDateTime createTime;   // 注册时间
}
