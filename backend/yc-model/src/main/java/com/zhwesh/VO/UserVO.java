package com.zhwesh.VO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 当前登录用户信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVO {
    private Long id;                  // 用户id
    private String username;          // 用户名
    private String name;              // 姓名
    private String image;             // 头像URL
    private String phone;             // 手机号
    private LocalDateTime createTime; // 注册时间
}
