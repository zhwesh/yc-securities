package com.zhwesh.service.impl;

import com.zhwesh.DTO.BindBankCardDTO;
import com.zhwesh.DTO.DeleteBankCardDTO;
import com.zhwesh.DTO.VerifyBankCardDTO;
import com.zhwesh.VO.BankCardVO;
import com.zhwesh.constant.AccountStatus;
import com.zhwesh.entity.Account;
import com.zhwesh.entity.BankCard;
import com.zhwesh.exception.BusinessException;
import com.zhwesh.mapper.AccountMapper;
import com.zhwesh.mapper.BankCardMapper;
import com.zhwesh.security.UserContext;
import com.zhwesh.service.BankCardService;
import com.zhwesh.util.BankChannelUtil;
import com.zhwesh.util.SmsCodeUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class BankCardServiceImpl implements BankCardService {
    private final BankCardMapper bankcardMapper;
    private final AccountMapper accountMapper;
    private final SmsCodeUtil smsCodeUtil;
    private final BankChannelUtil bankChannelUtil;

    @Autowired
    public BankCardServiceImpl(BankCardMapper bankcardMapper, AccountMapper accountMapper,
                               SmsCodeUtil smsCodeUtil,
                               BankChannelUtil bankChannelUtil) {
        this.bankcardMapper = bankcardMapper;
        this.accountMapper = accountMapper;
        this.smsCodeUtil = smsCodeUtil;
        this.bankChannelUtil = bankChannelUtil;
    }

    /**
     * 查询资金账户绑定的所有银行卡
     * @param accountId 资金账户id
     * @return 绑定的银行卡列表
     */
    @Override
    public List<BankCardVO> getBankCards(Long accountId) {
        Account account = accountMapper.getById(accountId);
        if (account == null) {
            throw new BusinessException("资金账户不存在");
        }
        if (!account.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException("资金账户不属于当前用户");
        }
        List<BankCard> bankCards = bankcardMapper.getByAccountId(accountId);
        List<BankCardVO> bankCardVOs = new ArrayList<>();
        for (BankCard bankCard : bankCards) {
            BankCardVO bankCardVO = new BankCardVO();
            BeanUtils.copyProperties(bankCard, bankCardVO);
            bankCardVO.setBindTime(bankCard.getCreateTime());
            // 使用掩码
            bankCardVO.setPhone(maskPhone(bankCard.getPhone()));
            bankCardVOs.add(bankCardVO);
        }
        return bankCardVOs;
    }

    /**
     * 解除银行卡绑定
     * @param deleteBankCardDTO 所需信息
     */
    @Override
    public void deleteBankCard(DeleteBankCardDTO deleteBankCardDTO) {
        BankCard bankCard = bankcardMapper.getByCardNumber(deleteBankCardDTO.getCardNumber());
        if (bankCard == null) {
            throw new BusinessException("银行卡不存在");
        }
        if (!bankCard.getAccountId().equals(deleteBankCardDTO.getAccountId())) {
            throw new BusinessException("银行卡不属于当前资金账户");
        }
        Account account = accountMapper.getById(bankCard.getAccountId());
        if (account == null) {
            throw new BusinessException("资金账户不存在");
        }
        if (!account.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException("资金账户不属于当前用户");
        }
        if (account.getStatus().equals(AccountStatus.DISABLE)) {
            throw new BusinessException("资金账户已被冻结");
        }

        // 校验预留手机号与短信验证码
        if (!Objects.equals(bankCard.getPhone(), deleteBankCardDTO.getPhone())) {
            throw new BusinessException("手机号与银行卡预留手机号不一致");
        }
        smsCodeUtil.verifyCode(deleteBankCardDTO.getPhone(), deleteBankCardDTO.getCode());

        // 银行渠道协议解约
        bankChannelUtil.unbindCard(deleteBankCardDTO.getCardNumber());

        bankcardMapper.deleteByCardNumber(deleteBankCardDTO.getCardNumber());
    }

    /**
     * 绑定银行卡
     * @param bindBankCardDTO 所需信息
     */
    @Override
    public void bindBankCard(BindBankCardDTO bindBankCardDTO) {
        BankCard bankCard = bankcardMapper.getByCardNumber(bindBankCardDTO.getCardNumber());
        if (bankCard != null) {
            throw new BusinessException("该银行卡已被使用");
        }
        Account account = accountMapper.getById(bindBankCardDTO.getAccountId());
        if (account == null) {
            throw new BusinessException("资金账户不存在");
        }
        if (!account.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException("资金账户不属于当前用户");
        }
        if (account.getStatus().equals(AccountStatus.DISABLE)) {
            throw new BusinessException("资金账户已被冻结");
        }

        // 校验短信验证码（发送至银行预留手机号）
        smsCodeUtil.verifyCode(bindBankCardDTO.getPhone(), bindBankCardDTO.getCode());

        // 银行渠道卡号与预留手机号匹配校验
        bankChannelUtil.checkCardPhone(bindBankCardDTO.getCardNumber(), bindBankCardDTO.getPhone());

        bankCard = BankCard.builder()
            .cardNumber(bindBankCardDTO.getCardNumber())
            .bankName(bindBankCardDTO.getBankName())
            .accountId(account.getId())
            .phone(bindBankCardDTO.getPhone())
            .build();
        bankcardMapper.insertBankCard(bankCard);
    }

    /**
     * 发送银行卡短信验证码（绑卡/解绑共用，发送至指定的手机号）
     * @param phone 银行预留手机号
     */
    @Override
    public void sendCode(String phone) {
        if (phone == null || !phone.matches("1\\d{10}")) {
            throw new BusinessException("手机号格式不正确");
        }
        smsCodeUtil.sendCode(phone);
    }

    /**
     * 验证银行卡号与预留手机号是否匹配
     * @param verifyBankCardDTO 验证信息
     */
    @Override
    public void verifyCardPhone(VerifyBankCardDTO verifyBankCardDTO) {
        BankCard bankCard = bankcardMapper.getByCardNumber(verifyBankCardDTO.getCardNumber());
        if (bankCard != null) {
            // 已绑定的银行卡：与绑定记录中的预留手机号比对
            if (!Objects.equals(bankCard.getPhone(), verifyBankCardDTO.getPhone())) {
                throw new BusinessException("手机号与银行卡预留手机号不一致");
            }
        } else {
            // 未绑定的银行卡：走银行侧校验（模拟）
            bankChannelUtil.checkCardPhone(verifyBankCardDTO.getCardNumber(), verifyBankCardDTO.getPhone());
        }
    }

    /**
     * 手机号掩码（138****8000）
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
