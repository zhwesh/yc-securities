package com.zhwesh.controller;

import com.zhwesh.DTO.BindBankCardDTO;
import com.zhwesh.DTO.DeleteBankCardDTO;
import com.zhwesh.DTO.VerifyBankCardDTO;
import com.zhwesh.VO.BankCardVO;
import com.zhwesh.result.Result;
import com.zhwesh.service.BankCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 银行卡相关接口
 */
@Slf4j
@Tag(name = "银行卡相关接口")
@RestController
@RequestMapping("/bank-card")
public class BankCardController {
    private final BankCardService bankcardService;

    @Autowired
    public BankCardController(BankCardService bankcardService) {
        this.bankcardService = bankcardService;
    }

    /**
     * 查询资金账户绑定的所有银行卡
     * @param accountId 资金账户id
     * @return 标准结果Result-List<BankCardVO>（绑定的银行卡列表）
     */
    @Operation(summary = "查询资金账户绑定的所有银行卡")
    @GetMapping
    public Result<List<BankCardVO>> getBankCards(@RequestParam("accountId") Long accountId) {
        log.info("查询资金账户绑定的所有银行卡：{}", accountId);
        return Result.success(bankcardService.getBankCards(accountId));
    }

    /**
     * 绑定银行卡
     * @param bindBankCardDTO 所需信息
     * @return 标准结果Result-Void
     */
    @Operation(summary = "绑定银行卡")
    @PostMapping
    public Result<Void> bindBankCard(@RequestBody BindBankCardDTO bindBankCardDTO) {
        log.info("绑定银行卡：{}", bindBankCardDTO);
        bankcardService.bindBankCard(bindBankCardDTO);
        return Result.success();
    }

    /**
     * 解除银行卡绑定
     * @param deleteBankCardDTO 所需信息
     * @return 标准结果Result-Void
     */
    @Operation(summary = "解除银行卡绑定")
    @DeleteMapping
    public Result<Void> deleteBankCard(@RequestBody DeleteBankCardDTO deleteBankCardDTO) {
        log.info("解除银行卡绑定：{}", deleteBankCardDTO);
        bankcardService.deleteBankCard(deleteBankCardDTO);
        return Result.success();
    }

    /**
     * 发送银行卡短信验证码
     * @param phone 银行预留手机号
     * @return 标准结果Result-Void
     */
    @Operation(summary = "发送银行卡验证码")
    @GetMapping("/code")
    public Result<Void> sendCode(@RequestParam("phone") String phone) {
        log.info("发送银行卡验证码：{}", phone);
        bankcardService.sendCode(phone);
        return Result.success();
    }

    /**
     * 验证银行卡号与预留手机号是否匹配
     * @param verifyBankCardDTO 验证信息
     * @return 标准结果Result-Void
     */
    @Operation(summary = "验证卡号与预留手机号")
    @PostMapping("/verify")
    public Result<Void> verifyCardPhone(@RequestBody VerifyBankCardDTO verifyBankCardDTO) {
        log.info("验证银行卡号与预留手机号：{}", verifyBankCardDTO);
        bankcardService.verifyCardPhone(verifyBankCardDTO);
        return Result.success();
    }
}
