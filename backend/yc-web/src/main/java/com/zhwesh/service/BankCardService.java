package com.zhwesh.service;

import com.zhwesh.DTO.BindBankCardDTO;
import com.zhwesh.DTO.DeleteBankCardDTO;
import com.zhwesh.DTO.VerifyBankCardDTO;
import com.zhwesh.VO.BankCardVO;

import java.util.List;

public interface BankCardService {
    /**
     * 查询资金账户绑定的所有银行卡
     * @param accountId 资金账户id
     * @return 绑定的银行卡列表
     */
    List<BankCardVO> getBankCards(Long accountId);

    /**
     * 绑定银行卡
     * @param bindBankCardDTO 所需信息
     */
    void bindBankCard(BindBankCardDTO bindBankCardDTO);

    /**
     * 解除银行卡绑定
     * @param deleteBankCardDTO 所需信息
     */
    void deleteBankCard(DeleteBankCardDTO deleteBankCardDTO);

    /**
     * 发送银行卡短信验证码（绑卡/解绑共用，发送至指定的手机号）
     * @param phone 银行预留手机号
     */
    void sendCode(String phone);

    /**
     * 验证银行卡号与预留手机号是否匹配
     * （已绑定的银行卡与绑定记录比对，未绑定的银行卡走银行侧校验）
     * @param verifyBankCardDTO 验证信息
     */
    void verifyCardPhone(VerifyBankCardDTO verifyBankCardDTO);
}
