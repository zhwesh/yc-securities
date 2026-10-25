package com.zhwesh.service;

import com.zhwesh.DTO.DepositDTO;
import com.zhwesh.DTO.OpenAccountDTO;
import com.zhwesh.DTO.OpenBoardDTO;
import com.zhwesh.DTO.UpdateTradePasswordDTO;
import com.zhwesh.DTO.VerifyTradePasswordDTO;
import com.zhwesh.DTO.WithdrawDTO;
import com.zhwesh.VO.AccountVO;

import java.util.List;

public interface AccountService {
    /**
     * 获取当前用户所有资产账户
     * @return 标准结果Result-List<AccountVO>（资金账户列表）
     */
    List<AccountVO> getCurrentAccount();

    /**
     * 开户
     * @param openAccountDTO 开户所需信息
     */
    void insertAccount(OpenAccountDTO openAccountDTO);

    /**
     * 开通板块交易权限
     * @param openBoardDTO 所需信息
     */
    void openBoard(OpenBoardDTO openBoardDTO);

    /**
     * 更改交易密码
     * @param updateTradePasswordDTO 所需信息
     */
    void updatePassword(UpdateTradePasswordDTO updateTradePasswordDTO);

    /**
     * 验证交易密码（成功后 24 小时内保持在线）
     * @param verifyTradePasswordDTO 验证信息
     */
    void verifyTradePassword(VerifyTradePasswordDTO verifyTradePasswordDTO);

    /**
     * 校验交易密码在线态，不在线（未验证或已过期）抛出异常
     * @param accountId 资金账户id
     */
    void checkTradeOnline(Long accountId);

    /**
     * 入金（银证转账：银行→券商）
     * @param depositDTO 入金信息
     */
    void deposit(DepositDTO depositDTO);

    /**
     * 出金（银证转账：券商→银行）
     * @param withdrawDTO 出金信息
     */
    void withdraw(WithdrawDTO withdrawDTO);
}
