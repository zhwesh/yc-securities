package com.zhwesh.service.impl;

import com.zhwesh.DTO.*;
import com.zhwesh.VO.LoginVO;
import com.zhwesh.VO.UserVO;
import com.zhwesh.entity.User;
import com.zhwesh.exception.BusinessException;
import com.zhwesh.mapper.UserMapper;
import com.zhwesh.security.UserContext;
import com.zhwesh.service.UserService;
import com.zhwesh.util.JwtUtil;
import com.zhwesh.util.SmsCodeUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final SmsCodeUtil smsCodeUtil;
    private final BCryptPasswordEncoder passwordEncoder;

    @Autowired
    public UserServiceImpl(UserMapper userMapper, JwtUtil jwtUtil, SmsCodeUtil smsCodeUtil, BCryptPasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.smsCodeUtil = smsCodeUtil;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 用户注册
     * @param registerDTO 注册信息
     * @return 登录返回信息
     */
    @Override
    public LoginVO register(RegisterDTO registerDTO) {
        // 检查用户名、手机号是否已被使用
        if (userMapper.getByUsername(registerDTO.getUsername()) != null) {
            throw new BusinessException("用户名已存在");
        }
        if (userMapper.getByPhone(registerDTO.getPhone()) != null) {
            throw new BusinessException("手机号已注册");
        }

        // 检查验证码
        smsCodeUtil.verifyCode(registerDTO.getPhone(), registerDTO.getCode());

        User user = new User();
        BeanUtils.copyProperties(registerDTO, user);

        // 密码加密
        user.setPassword(passwordEncoder.encode(registerDTO.getPassword()));

        userMapper.insertUser(user);

        // 生成jwt令牌
        String token = jwtUtil.generateJwt(user.getId(), user.getUsername());

        // 返回值
        return LoginVO.builder()
            .token(token)
            .id(user.getId())
            .username(user.getUsername())
            .name(user.getName())
            .image(user.getImage())
            .phone(user.getPhone())
            .createTime(user.getCreateTime())
            .build();
    }

    /**
     * 通过用户名登录
     * @param usernameLoginDTO 用户名登录信息
     * @return 登录返回信息
     */
    @Override
    public LoginVO usernameLogin(UsernameLoginDTO usernameLoginDTO) {
        String username = usernameLoginDTO.getUsername();

        // 根据用户名查询数据库中的数据
        User user = userMapper.getByUsername(username);

        // 处理异常情况
        if (user == null) {
            // 账号不存在
            throw new BusinessException("账号不存在");
        }

        // 密码比对
        if (!passwordEncoder.matches(usernameLoginDTO.getPassword(), user.getPassword())) {
            // 密码错误
            throw new BusinessException("密码错误");
        }

        // 生成jwt令牌
        String token = jwtUtil.generateJwt(user.getId(), username);

        // 返回值
        return LoginVO.builder()
            .token(token)
            .id(user.getId())
            .username(user.getUsername())
            .name(user.getName())
            .image(user.getImage())
            .phone(user.getPhone())
            .createTime(user.getCreateTime())
            .build();
    }

    /**
     * 通过手机验证码登录
     * @param phoneLoginDTO 用户名登录信息
     * @return 登录返回信息
     */
    @Override
    public LoginVO phoneLogin(PhoneLoginDTO phoneLoginDTO) {
        // 检查验证码
        smsCodeUtil.verifyCode(phoneLoginDTO.getPhone(), phoneLoginDTO.getCode());

        // 根据手机号查询数据库中的数据
        User user = userMapper.getByPhone(phoneLoginDTO.getPhone());

        // 处理异常情况
        if (user == null) {
            // 手机号未注册
            throw new BusinessException("手机号未注册");
        }

        // 生成jwt令牌
        String token = jwtUtil.generateJwt(user.getId(), user.getUsername());

        // 返回值
        return LoginVO.builder()
            .token(token)
            .id(user.getId())
            .username(user.getUsername())
            .name(user.getName())
            .image(user.getImage())
            .phone(user.getPhone())
            .createTime(user.getCreateTime())
            .build();
    }

    /**
     * 查询当前登录的用户信息
     * @return 用户信息
     */
    @Override
    public UserVO getCurrentUser() {
        // 根据id查询用户
        Long id = UserContext.getUserId();
        User user = userMapper.getById(id);

        // 用户不存在，令牌无效，登录状态无效
        if (user == null) {
            throw new BusinessException("登录状态无效");
        }

        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    /**
     * 修改用户信息
     * @param updateUserDTO 待修改信息
     */
    @Override
    public void updateUser(UpdateUserDTO updateUserDTO) {
        Long id = UserContext.getUserId();

        // 修改手机号时，检查新手机号的验证码
        if (updateUserDTO.getPhone() != null) {
            smsCodeUtil.verifyCode(updateUserDTO.getPhone(), updateUserDTO.getCode());
        }

        // 检查用户名、手机号是否已被其他账号使用
        if (updateUserDTO.getUsername() != null) {
            User user = userMapper.getByUsername(updateUserDTO.getUsername());
            if (user != null && !user.getId().equals(id)) {
                throw new BusinessException("用户名已存在");
            }
        }
        if (updateUserDTO.getPhone() != null) {
            User user = userMapper.getByPhone(updateUserDTO.getPhone());
            if (user != null && !user.getId().equals(id)) {
                throw new BusinessException("手机号已注册");
            }
        }

        User user = User.builder()
            .id(id)
            .username(updateUserDTO.getUsername())
            .image(updateUserDTO.getImage())
            .phone(updateUserDTO.getPhone())
            .build();

        userMapper.updateUser(user);
    }

    /**
     * 重置密码
     * @param resetPasswordDTO 重置密码所需信息
     */
    @Override
    public void resetPassword(ResetPasswordDTO resetPasswordDTO) {
        // 检查验证码
        smsCodeUtil.verifyCode(resetPasswordDTO.getPhone(), resetPasswordDTO.getCode());

        // 手机号必须已注册
        if (userMapper.getByPhone(resetPasswordDTO.getPhone()) == null) {
            throw new BusinessException("手机号未注册");
        }

        // 密码密文
        String password = passwordEncoder.encode(resetPasswordDTO.getNewPassword());

        userMapper.resetPassword(resetPasswordDTO.getPhone(), password);
    }
}
