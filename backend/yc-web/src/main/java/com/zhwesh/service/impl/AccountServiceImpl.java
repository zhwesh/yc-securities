package com.zhwesh.service.impl;

import com.zhwesh.DTO.DepositDTO;
import com.zhwesh.DTO.OpenAccountDTO;
import com.zhwesh.DTO.OpenBoardDTO;
import com.zhwesh.DTO.UpdateTradePasswordDTO;
import com.zhwesh.DTO.VerifyTradePasswordDTO;
import com.zhwesh.DTO.WithdrawDTO;
import com.zhwesh.VO.AccountVO;
import com.zhwesh.constant.AccountStatus;
import com.zhwesh.constant.Board;
import com.zhwesh.entity.Account;
import com.zhwesh.entity.BankCard;
import com.zhwesh.exception.BusinessException;
import com.zhwesh.mapper.AccountMapper;
import com.zhwesh.mapper.BankCardMapper;
import com.zhwesh.security.UserContext;
import com.zhwesh.service.AccountService;
import com.zhwesh.util.FundingChannelUtil;
import com.zhwesh.util.RedisUtil;
import com.zhwesh.util.StockUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class AccountServiceImpl implements AccountService {
    /**
     * 交易密码在线态的key前缀
     */
    private static final String TRADE_ONLINE_KEY_PREFIX = "trade:online:";

    /**
     * 允许的保持在线时长（小时）
     */
    private static final Set<Integer> ALLOWED_ONLINE_HOURS = Set.of(6, 12, 24);

    private final AccountMapper accountMapper;
    private final BankCardMapper bankCardMapper;
    private final FundingChannelUtil fundingChannelUtil;
    private final BCryptPasswordEncoder passwordEncoder;
    private final RedisUtil redisUtil;

    @Autowired
    public AccountServiceImpl(AccountMapper accountMapper, BankCardMapper bankCardMapper,
        FundingChannelUtil fundingChannelUtil,
        BCryptPasswordEncoder passwordEncoder, RedisUtil redisUtil) {
        this.accountMapper = accountMapper;
        this.bankCardMapper = bankCardMapper;
        this.fundingChannelUtil = fundingChannelUtil;
        this.passwordEncoder = passwordEncoder;
        this.redisUtil = redisUtil;
    }

    /**
     * 获取当前用户所有资产账户
     * @return 标准结果Result-List<AccountVO>（资金账户列表）
     */
    @Override
    public List<AccountVO> getCurrentAccount() {
        List<Account> accounts = accountMapper.getByUserId(UserContext.getUserId());
        List<AccountVO> accountVOs = new ArrayList<>();
        for (Account account : accounts) {
            AccountVO accountVO = new AccountVO();
            BeanUtils.copyProperties(account, accountVO);
            accountVO.setBoards(StockUtil.parseBoards(account.getBoards()));
            accountVOs.add(accountVO);
        }
        return accountVOs;
    }

    /**
     * 开户
     * @param openAccountDTO 开户所需信息
     */
    @Override
    public void insertAccount(OpenAccountDTO openAccountDTO) {
        Account account = Account.builder()
            .password(passwordEncoder.encode(openAccountDTO.getPassword()))
            .userId(UserContext.getUserId())
            .availableAssets(new BigDecimal(0))
            .boards(Board.MAIN_BOARD)
            .status(AccountStatus.ENABLE)
            .build();

        accountMapper.insertAccount(account);
    }

    /**
     * 开通板块交易权限
     * @param openBoardDTO 所需信息
     */
    @Override
    public void openBoard(OpenBoardDTO openBoardDTO) {
        List<String> boards = openBoardDTO.getBoards();
        // 参数校验
        if (boards == null || boards.isEmpty()) {
            throw new BusinessException("板块列表不能为空");
        }

        // 查询资金账户
        Account account = accountMapper.getById(openBoardDTO.getAccountId());
        if (account == null) {
            throw new BusinessException("资金账户不存在");
        }
        if (account.getStatus().equals(AccountStatus.DISABLE)) {
            throw new BusinessException("资金账户已被冻结");
        }
        if (!account.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException("资金账户不属于当前用户");
        }

        // 获取原有板块
        HashSet<String> newBoards = new HashSet<>(StockUtil.parseBoards(account.getBoards()));
        BigDecimal availableAssets = account.getAvailableAssets() == null ?
            BigDecimal.ZERO : account.getAvailableAssets();
        for (String board : boards) {
            if (!Board.validBoardName.contains(board)) {
                throw new BusinessException("非法板块名：" + board);
            }
            // 已开通的板块直接忽略
            if (newBoards.contains(board)) {
                continue;
            }
            // 开通门槛校验（按可用资产判定）
            BigDecimal threshold = StockUtil.getOpenThreshold(board);
            if (availableAssets.compareTo(threshold) < 0) {
                throw new BusinessException("未达到开通条件：" + board + "需要可用资产达到"
                    + threshold.movePointLeft(4).stripTrailingZeros().toPlainString() + "万元");
            }
            newBoards.add(board);
        }

        // 写回
        accountMapper.setBoardsById(account.getId(), String.join(",", newBoards));
    }

    /**
     * 更改交易密码
     * @param updateTradePasswordDTO 所需信息
     */
    @Override
    public void updatePassword(UpdateTradePasswordDTO updateTradePasswordDTO) {
        Account account = accountMapper.getById(updateTradePasswordDTO.getAccountId());
        // 参数校验
        if (account == null) {
            throw new BusinessException("资金账户不存在");
        }
        if (account.getStatus().equals(AccountStatus.DISABLE)) {
            throw new BusinessException("资金账户已被冻结");
        }
        if (!account.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException("资金账户不属于当前用户");
        }
        if (!passwordEncoder.matches(updateTradePasswordDTO.getOldPassword(), account.getPassword())) {
            throw new BusinessException("原交易密码错误");
        }

        // 设置密码
        accountMapper.updatePasswordById(
            account.getId(),
            passwordEncoder.encode(updateTradePasswordDTO.getNewPassword())
        );
        // 密码变更后清除在线态，需重新验证
        redisUtil.delete(TRADE_ONLINE_KEY_PREFIX + account.getId());
    }

    /**
     * 验证交易密码（成功后 24 小时内保持在线）
     * @param verifyTradePasswordDTO 验证信息
     */
    @Override
    public void verifyTradePassword(VerifyTradePasswordDTO verifyTradePasswordDTO) {
        // 参数校验
        Integer onlineHours = verifyTradePasswordDTO.getOnlineHours();
        if (onlineHours == null || !ALLOWED_ONLINE_HOURS.contains(onlineHours)) {
            throw new BusinessException("不支持的保持在线时长，支持：6、12、24 小时");
        }

        // 查询资金账户
        Account account = accountMapper.getById(verifyTradePasswordDTO.getAccountId());
        if (account == null) {
            throw new BusinessException("资金账户不存在");
        }
        if (account.getStatus().equals(AccountStatus.DISABLE)) {
            throw new BusinessException("资金账户已被冻结");
        }
        if (!account.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException("资金账户不属于当前用户");
        }
        // 校验交易密码
        if (!passwordEncoder.matches(verifyTradePasswordDTO.getTradePassword(), account.getPassword())) {
            throw new BusinessException("交易密码错误");
        }

        // 建立在线态
        redisUtil.setEx(TRADE_ONLINE_KEY_PREFIX + account.getId(), "1", onlineHours * 3600L);
    }

    /**
     * 校验交易密码在线态，不在线（未验证或已过期）抛出异常
     * @param accountId 资金账户id
     */
    @Override
    public void checkTradeOnline(Long accountId) {
        if (!redisUtil.hasKey(TRADE_ONLINE_KEY_PREFIX + accountId)) {
            throw new BusinessException("请先验证交易密码");
        }
    }

    /**
     * 入金（银证转账：银行→券商）
     * @param depositDTO 入金信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deposit(DepositDTO depositDTO) {
        Account account = getEnabledOwnedAccount(depositDTO.getAccountId());
        BigDecimal amount = normalizeAmount(depositDTO.getAmount(), "入金金额");
        BankCard bankCard = getBoundBankCard(account.getId());

        String tradeNo = fundingChannelUtil.deposit(
            account.getId(), bankCard.getCardNumber(), amount);
        accountMapper.addAssets(account.getId(), amount);

        log.info("入金成功：accountId={}, amount={}, tradeNo={}", account.getId(), amount, tradeNo);
    }

    /**
     * 出金（银证转账：券商→银行）
     * @param withdrawDTO 出金信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(WithdrawDTO withdrawDTO) {
        Account account = getEnabledOwnedAccount(withdrawDTO.getAccountId());
        checkTradeOnline(account.getId());
        BigDecimal amount = normalizeAmount(withdrawDTO.getAmount(), "出金金额");
        BankCard bankCard = getBoundBankCard(account.getId());

        if (accountMapper.deductAssets(account.getId(), amount) == 0) {
            throw new BusinessException("可用余额不足");
        }
        String tradeNo = fundingChannelUtil.withdraw(
            account.getId(), bankCard.getCardNumber(), amount);

        log.info("出金成功：accountId={}, amount={}, tradeNo={}", account.getId(), amount, tradeNo);
    }

    /**
     * 校验并获取可用资金账户（存在、未冻结且属于当前用户）
     */
    private Account getEnabledOwnedAccount(Long accountId) {
        Account account = accountMapper.getById(accountId);
        if (account == null) {
            throw new BusinessException("资金账户不存在");
        }
        if (account.getStatus().equals(AccountStatus.DISABLE)) {
            throw new BusinessException("资金账户已被冻结");
        }
        if (!account.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException("资金账户不属于当前用户");
        }
        return account;
    }

    /**
     * 金额校验并规范为2位小数
     */
    private BigDecimal normalizeAmount(BigDecimal amount, String name) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(name + "必须大于0");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 获取银证转账使用的绑定银行卡（默认第一张）
     */
    private BankCard getBoundBankCard(Long accountId) {
        List<BankCard> bankCards = bankCardMapper.getByAccountId(accountId);
        if (bankCards == null || bankCards.isEmpty()) {
            throw new BusinessException("请先绑定银行卡");
        }
        return bankCards.get(0);
    }
}
