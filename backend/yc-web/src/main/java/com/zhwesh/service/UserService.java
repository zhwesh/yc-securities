package com.zhwesh.service;

import com.zhwesh.DTO.*;
import com.zhwesh.VO.LoginVO;
import com.zhwesh.VO.UserVO;
import com.zhwesh.exception.BusinessException;

public interface UserService {
    /**
     * 用户注册
     * @param registerDTO 注册信息
     * @return 登录返回信息
     */
    LoginVO register(RegisterDTO registerDTO);

    /**
     * 通过用户名登录
     * @param usernameLoginDTO 用户名登录信息
     * @return 登录返回信息
     */
    LoginVO usernameLogin(UsernameLoginDTO usernameLoginDTO);

    /**
     * 通过手机验证码登录
     * @param phoneLoginDTO 用户名登录信息
     * @return 登录返回信息
     */
    LoginVO phoneLogin(PhoneLoginDTO phoneLoginDTO);

    /**
     * 查询当前登录的用户信息
     * @return 用户信息
     */
    UserVO getCurrentUser();

    /**
     * 修改用户信息
     * @param updateUserDTO 待修改信息
     */
    void updateUser(UpdateUserDTO updateUserDTO);

    /**
     * 重置密码
     * @param resetPasswordDTO 重置密码所需信息
     */
    public void resetPassword(ResetPasswordDTO resetPasswordDTO);
}
