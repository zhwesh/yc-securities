package com.zhwesh.controller;

import com.zhwesh.DTO.*;
import com.zhwesh.VO.LoginVO;
import com.zhwesh.VO.UserVO;
import com.zhwesh.result.Result;
import com.zhwesh.service.UserService;
import com.zhwesh.util.AliOssUtil;
import com.zhwesh.util.SmsCodeUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 用户相关接口
 */
@Tag(name = "用户相关接口")
@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {
    private final SmsCodeUtil smsCodeUtil;
    private final AliOssUtil aliOssUtil;
    private final UserService userService;

    @Autowired
    public UserController(
        SmsCodeUtil smsCodeUtil, AliOssUtil aliOssUtil,
        UserService userService
    ) {
        this.smsCodeUtil = smsCodeUtil;
        this.aliOssUtil = aliOssUtil;
        this.userService = userService;
    }

    /**
     * 获取短信验证码
     */
    @Operation(summary = "获取短信验证码")
    @GetMapping("/code")
    public Result<Void> getPhoneCode(@RequestParam("phone") String phone) {
        smsCodeUtil.sendCode(phone);
        return Result.success();
    }

    /**
     * 用户注册
     * @param registerDTO 注册信息
     * @return 标准结果Result-LoginVO（登录返回信息）
     */
    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<LoginVO> register(@RequestBody RegisterDTO registerDTO) {
        log.info("用户注册：{}", registerDTO);
        return Result.success(userService.register(registerDTO));
    }

    /**
     * 通过用户名登录
     * @param usernameLoginDTO 用户名登录信息
     * @return 标准结果Result-LoginVO（登录返回信息）
     */
    @Operation(summary = "通过用户名登录")
    @PostMapping("/login/username")
    public Result<LoginVO> usernameLogin(@RequestBody UsernameLoginDTO usernameLoginDTO) {
        log.info("通过用户名登录：{}", usernameLoginDTO);
        return Result.success(userService.usernameLogin(usernameLoginDTO));
    }

    /**
     * 通过手机验证码登录
     * @param phoneLoginDTO 手机验证码登录信息
     * @return 标准结果Result-LoginVO（登录返回信息）
     */
    @Operation(summary = "通过手机验证码登录")
    @PostMapping("/login/phone")
    public Result<LoginVO> phoneLogin(@RequestBody PhoneLoginDTO phoneLoginDTO) {
        log.info("通过手机验证码登录：{}", phoneLoginDTO);
        return Result.success(userService.phoneLogin(phoneLoginDTO));
    }

    /**
     * 用户上传头像
     * @param file 头像
     * @return 标准结果Result-String（头像url）
     */
    @Operation(summary = "上传头像")
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestPart("file") MultipartFile file) throws IOException {
        log.info("上传头像：{}", file);
        return Result.success(aliOssUtil.upload(file));
    }

    /**
     * 查询当前登录的用户信息
     * @return 标准结果Result-UserVO（用户信息）
     */
    @Operation(summary = "查询用户信息")
    @GetMapping
    public Result<UserVO> getCurrentUser() {
        log.info("查询用户信息");
        return Result.success(userService.getCurrentUser());
    }

    /**
     * 修改用户信息
     * @param updateUserDTO 待修改信息
     * @return 标准结果Result-Void
     */
    @Operation(summary = "修改用户信息")
    @PutMapping("/update")
    public Result<Void> updateUser(@RequestBody UpdateUserDTO updateUserDTO) {
        log.info("修改用户信息：{}", updateUserDTO);
        userService.updateUser(updateUserDTO);
        return Result.success();
    }

    /**
     * 重置密码
     * @param resetPasswordDTO 重置密码所需信息
     * @return 标准结果Result-Void
     */
    @Operation(summary = "重置密码")
    @PutMapping("/password/reset")
    public Result<Void> resetPassword(@RequestBody ResetPasswordDTO resetPasswordDTO) {
        log.info("重置密码：{}", resetPasswordDTO);
        userService.resetPassword(resetPasswordDTO);
        return Result.success();
    }
}
