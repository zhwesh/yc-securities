package com.zhwesh.mapper;

import com.zhwesh.annotation.AutoFill;
import com.zhwesh.entity.Account;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface AccountMapper {
    /**
     * 按userId获取资金账户列表
     * @param userId 用户id
     * @return 资金账户列表
     */
    @Select("SELECT * FROM account WHERE user_id = #{userId}")
    List<Account> getByUserId(Long userId);

    /**
     * 插入资金账户
     * @param account 资金账户信息
     */
    @AutoFill
    @Options(useGeneratedKeys = true, keyProperty = "id")
    @Insert("INSERT INTO account(password, user_id, available_assets, boards, create_time, status)" +
        "VALUES(#{password}, #{userId}, #{availableAssets}, #{boards}, #{createTime}, #{status})")
    void insertAccount(Account account);

    /**
     * 按id获取对应资金账户
     * @param id 资金账户id
     * @return 资金账户
     */
    @Select("SELECT * FROM account WHERE id = #{id}")
    Account getById(Long id);

    /**
     * 按id设置对应资金账户的可交易板块
     * @param id 资金账户id
     * @param boards 可交易板块，逗号分隔
     */
    @Update("UPDATE account SET boards = #{boards} WHERE id = #{id}")
    void setBoardsById(@Param("id") Long id, @Param("boards") String boards);

    /**
     * 按id修改密码
     * @param id 资金账户id
     * @param password 新密码
     */
    @Update("UPDATE account SET password = #{password} WHERE id = #{id}")
    void updatePasswordById(@Param("id") Long id, @Param("password") String password);

    /**
     * 冻结资金（可用资产转入冻结资产）
     * @param id 资金账户id
     * @param amount 冻结金额
     * @return 受影响行数（0 表示可用资产不足）
     */
    @Update("UPDATE account SET available_assets = available_assets - #{amount}, " +
        "frozen_assets = frozen_assets + #{amount} " +
        "WHERE id = #{id} AND available_assets >= #{amount}")
    int freezeAssets(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /**
     * 买入成交结算：扣减冻结资金，差额退回可用资产
     * @param id 资金账户id
     * @param frozenAmount 按冻结价计算的成交金额
     * @param cost 实际成交金额
     * @return 受影响行数（0 表示冻结资金不足）
     */
    @Update("UPDATE account SET frozen_assets = frozen_assets - #{frozenAmount}, " +
        "available_assets = available_assets + #{frozenAmount} - #{cost} " +
        "WHERE id = #{id} AND frozen_assets >= #{frozenAmount}")
    int settleBuy(@Param("id") Long id, @Param("frozenAmount") BigDecimal frozenAmount,
        @Param("cost") BigDecimal cost);

    /**
     * 解冻资金（撤单/市价单剩余撤销）
     * @param id 资金账户id
     * @param amount 解冻金额
     * @return 受影响行数
     */
    @Update("UPDATE account SET frozen_assets = frozen_assets - #{amount}, " +
        "available_assets = available_assets + #{amount} " +
        "WHERE id = #{id} AND frozen_assets >= #{amount}")
    int unfreezeAssets(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /**
     * 增加可用资产（卖出成交/入金）
     * @param id 资金账户id
     * @param amount 金额
     */
    @Update("UPDATE account SET available_assets = available_assets + #{amount} WHERE id = #{id}")
    void addAssets(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /**
     * 扣减可用资产（出金）
     * @param id 资金账户id
     * @param amount 金额
     * @return 受影响行数（0 表示可用余额不足）
     */
    @Update("UPDATE account SET available_assets = available_assets - #{amount} " +
        "WHERE id = #{id} AND available_assets >= #{amount}")
    int deductAssets(@Param("id") Long id, @Param("amount") BigDecimal amount);
}
