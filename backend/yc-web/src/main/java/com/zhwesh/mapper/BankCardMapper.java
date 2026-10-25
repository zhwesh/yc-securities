package com.zhwesh.mapper;

import com.zhwesh.annotation.AutoFill;
import com.zhwesh.entity.BankCard;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BankCardMapper {
    /**
     * 根据资金账户id查询银行卡
     * @param accountId 资金账户id
     * @return 绑定的银行卡列表
     */
    @Select("SELECT * FROM bank_card WHERE account_id = #{accountId}")
    List<BankCard> getByAccountId(Long accountId);

    /**
     * 按银行卡卡号查询银行卡
     * @param cardNumber 银行卡号
     * @return 银行卡
     */
    @Select("SELECT * FROM bank_card WHERE card_number = #{cardNumber}")
    BankCard getByCardNumber(String cardNumber);

    /**
     * 按银行卡卡号删除银行卡
     * @param cardNumber 银行卡号
     */
    @Delete("DELETE FROM bank_card WHERE card_number = #{cardNumber}")
    void deleteByCardNumber(String cardNumber);

    /**
     * 插入银行卡
     * @param bankCard 银行卡
     */
    @AutoFill
    @Insert("INSERT INTO bank_card(card_number, bank_name, account_id, phone, create_time)" +
        "VALUES(#{cardNumber}, #{bankName}, #{accountId}, #{phone}, #{createTime})")
    void insertBankCard(BankCard bankCard);
}
