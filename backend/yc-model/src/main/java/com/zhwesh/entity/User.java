package com.zhwesh.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private Long id;                    // id
    private String password;            // 密码
    private String name;                // 姓名
    private String idNumber;            // 身份证号
    private String username;            // 用户名
    private String phone;               // 手机号
    private String image;               // 头像
    private LocalDateTime createTime;   // 注册时间
}
