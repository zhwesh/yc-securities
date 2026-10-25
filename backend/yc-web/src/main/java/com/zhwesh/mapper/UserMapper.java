package com.zhwesh.mapper;

import com.zhwesh.annotation.AutoFill;
import com.zhwesh.entity.User;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserMapper {
    /**
     * 插入新用户
     * @param user 新用户
     */
    @AutoFill
    @Options(useGeneratedKeys = true, keyProperty = "id")
    @Insert("INSERT INTO user(password, name, id_number, username, phone, image, create_time)" +
        "VALUES(#{password}, #{name}, #{idNumber}, #{username}, #{phone}, #{image}, #{createTime})")
    void insertUser(User user);

    /**
     * 按用户名查询
     * @param username 用户名
     * @return 结果
     */
    @Select("SELECT * FROM user WHERE username = #{username}")
    User getByUsername(String username);

    /**
     * 按手机号查询
     * @param phone 手机号
     * @return 结果
     */
    @Select("SELECT * FROM user WHERE phone = #{phone}")
    User getByPhone(String phone);

    /**
     * 按id查询
     * @param id 用户id
     * @return 结果
     */
    @Select("SELECT * FROM user WHERE id = #{id}")
    User getById(Long id);

    /**
     * 修改用户信息
     * @param user 待修改信息
     */
    void updateUser(User user);

    /**
     * 重置密码
     * @param phone 账户手机号
     * @param password 新密码
     */
    @Update("UPDATE user SET password = #{password} WHERE phone = #{phone}")
    void resetPassword(@Param("phone") String phone, @Param("password") String password);
}
