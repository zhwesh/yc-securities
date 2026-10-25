package com.zhwesh.controller;

import com.zhwesh.DTO.DepositDTO;
import com.zhwesh.DTO.OpenAccountDTO;
import com.zhwesh.DTO.OpenBoardDTO;
import com.zhwesh.DTO.UpdateTradePasswordDTO;
import com.zhwesh.DTO.VerifyTradePasswordDTO;
import com.zhwesh.DTO.WithdrawDTO;
import com.zhwesh.VO.AccountVO;
import com.zhwesh.result.Result;
import com.zhwesh.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 资金账户相关接口
 */
@Slf4j
@Tag(name = "资金账户相关接口")
@RestController
@RequestMapping("/account")
public class AccountController {
    public final AccountService accountService;

    @Autowired
    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * 获取当前用户所有资产账户
     * @return 标准结果Result-List<AccountVO>（资金账户列表）
     */
    @Operation(summary = "获取当前用户所有资产账户")
    @GetMapping
    public Result<List<AccountVO>> getCurrentAccount() {
        log.info("获取当前用户所有资产账户");
        return Result.success(accountService.getCurrentAccount());
    }

    /**
     * 开户
     * @param openAccountDTO 开户所需信息
     * @return 标准结果Result-Void
     */
    @Operation(summary = "开户")
    @PostMapping("/open")
    public Result<Void> insertAccount(@RequestBody OpenAccountDTO openAccountDTO) {
        log.info("开户：{}", openAccountDTO);
        accountService.insertAccount(openAccountDTO);
        return Result.success();
    }

    /**
     * 开通板块交易权限
     * @param openBoardDTO 所需信息
     * @return 标准结果Result-Void
     */
    @Operation(summary = "开通板块交易权限")
    @PutMapping("/board/open")
    public Result<Void> openBoard(@RequestBody OpenBoardDTO openBoardDTO) {
        log.info("开通板块交易权限：{}", openBoardDTO);
        accountService.openBoard(openBoardDTO);
        return Result.success();
    }

    /**
     * 更改交易密码
     * @param updateTradePasswordDTO 所需信息
     * @return 标准结果Result-Void
     */
    @Operation(summary = "更改交易密码")
    @PutMapping("/password/update")
    public Result<Void> updatePassword(@RequestBody UpdateTradePasswordDTO updateTradePasswordDTO) {
        log.info("更改交易密码：{}", updateTradePasswordDTO);
        accountService.updatePassword(updateTradePasswordDTO);
        return Result.success();
    }

    /**
     * 验证交易密码
     * @param verifyTradePasswordDTO 所需信息
     * @return 标准结果Result-Void
     */
    @Operation(summary = "验证交易密码")
    @PostMapping("/password/verify")
    public Result<Void> verifyTradePassword(@RequestBody VerifyTradePasswordDTO verifyTradePasswordDTO) {
        log.info("验证交易密码：{}", verifyTradePasswordDTO);
        accountService.verifyTradePassword(verifyTradePasswordDTO);
        return Result.success();
    }

    /**
     * 入金（银证转账：银行→券商）
     * @param depositDTO 入金信息
     * @return 标准结果Result-Void
     */
    @Operation(summary = "入金")
    @PutMapping("/deposit")
    public Result<Void> deposit(@RequestBody DepositDTO depositDTO) {
        log.info("入金：{}", depositDTO);
        accountService.deposit(depositDTO);
        return Result.success();
    }

    /**
     * 出金（银证转账：券商→银行）
     * @param withdrawDTO 出金信息
     * @return 标准结果Result-Void
     */
    @Operation(summary = "出金")
    @PutMapping("/withdraw")
    public Result<Void> withdraw(@RequestBody WithdrawDTO withdrawDTO) {
        log.info("出金：{}", withdrawDTO);
        accountService.withdraw(withdrawDTO);
        return Result.success();
    }
}
