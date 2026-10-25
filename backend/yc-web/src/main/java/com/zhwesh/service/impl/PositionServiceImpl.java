package com.zhwesh.service.impl;

import com.zhwesh.VO.PositionVO;
import com.zhwesh.VO.StockQuoteVO;
import com.zhwesh.engine.market.MarketEngine;
import com.zhwesh.entity.Account;
import com.zhwesh.entity.Position;
import com.zhwesh.exception.BusinessException;
import com.zhwesh.mapper.AccountMapper;
import com.zhwesh.mapper.PositionMapper;
import com.zhwesh.security.UserContext;
import com.zhwesh.service.AccountService;
import com.zhwesh.service.PositionService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class PositionServiceImpl implements PositionService {
    private final PositionMapper positionMapper;
    private final AccountMapper accountMapper;
    private final AccountService accountService;
    private final MarketEngine marketEngine;

    @Autowired
    public PositionServiceImpl(PositionMapper positionMapper, AccountMapper accountMapper,
        AccountService accountService, MarketEngine marketEngine) {
        this.positionMapper = positionMapper;
        this.accountMapper = accountMapper;
        this.accountService = accountService;
        this.marketEngine = marketEngine;
    }

    /**
     * 查询用户持仓
     * @param accountId 资金账户id
     * @return 持仓列表List<PositionVO>
     */
    @Override
    public List<PositionVO> getPositions(Long accountId) {
        Account account = accountMapper.getById(accountId);
        if (account == null) {
            throw new BusinessException("资金账户不存在");
        }
        if (!account.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException("无权查询其他用户的持仓");
        }
        accountService.checkTradeOnline(accountId);

        List<Position> positions = positionMapper.getPositions(accountId);
        List<PositionVO> positionVOs = new ArrayList<>(positions.size());
        for (Position position : positions) {
            PositionVO positionVO = new PositionVO();
            // 字段名/类型与实体一致（quantity/availableQuantity 均为 Long）
            BeanUtils.copyProperties(position, positionVO);
            fillMarketData(positionVO, position);
            positionVOs.add(positionVO);
        }
        return positionVOs;
    }

    /**
     * 用实时行情填充现价、市值、浮动盈亏
     */
    private void fillMarketData(PositionVO positionVO, Position position) {
        StockQuoteVO quote = marketEngine.getQuote(position.getCode());
        if (quote == null || quote.getPrice() == null) {
            return;
        }
        BigDecimal currentPrice = quote.getPrice();
        positionVO.setCurrentPrice(currentPrice);

        BigDecimal quantity = BigDecimal.valueOf(position.getQuantity());
        BigDecimal marketValue = currentPrice.multiply(quantity);
        positionVO.setMarketValue(marketValue);

        if (position.getCostPrice() == null || position.getCostPrice().signum() <= 0) {
            return;
        }
        BigDecimal costAmount = position.getCostPrice().multiply(quantity);
        BigDecimal profitLoss = marketValue.subtract(costAmount);
        positionVO.setProfitLoss(profitLoss);
        positionVO.setProfitLossRatio(profitLoss.divide(costAmount, 6, RoundingMode.HALF_UP));
    }
}
