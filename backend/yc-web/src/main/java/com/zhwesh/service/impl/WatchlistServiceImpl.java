package com.zhwesh.service.impl;

import com.zhwesh.DTO.AddWatchlistDTO;
import com.zhwesh.DTO.UpdateWatchlistDTO;
import com.zhwesh.VO.StockVO;
import com.zhwesh.entity.Stock;
import com.zhwesh.entity.Watchlist;
import com.zhwesh.exception.BusinessException;
import com.zhwesh.mapper.StockMapper;
import com.zhwesh.mapper.WatchlistMapper;
import com.zhwesh.security.UserContext;
import com.zhwesh.service.WatchlistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class WatchlistServiceImpl implements WatchlistService {

    /**
     * 自选股数量上限
     */
    private static final int MAX_WATCHLIST_SIZE = 50;

    private final WatchlistMapper watchlistMapper;
    private final StockMapper stockMapper;

    @Autowired
    public WatchlistServiceImpl(WatchlistMapper watchlistMapper, StockMapper stockMapper) {
        this.watchlistMapper = watchlistMapper;
        this.stockMapper = stockMapper;
    }

    /**
     * 查询当前用户自选股
     * @return 自选股票列表（代码+名称）
     */
    @Override
    public List<StockVO> getWatchlist() {
        return watchlistMapper.getStocksByUserId(UserContext.getUserId());
    }

    /**
     * 添加自选股
     * @param addWatchlistDTO 自选股信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addWatchlist(AddWatchlistDTO addWatchlistDTO) {
        String code = normalizeCode(addWatchlistDTO.getCode());
        Long userId = UserContext.getUserId();

        if (stockMapper.getStockByCode(code) == null) {
            throw new BusinessException("股票不存在");
        }
        if (watchlistMapper.getByUserIdAndCode(userId, code) != null) {
            throw new BusinessException("该股票已在自选中");
        }
        int count = watchlistMapper.countByUserId(userId);
        if (count >= MAX_WATCHLIST_SIZE) {
            throw new BusinessException("自选股数量已达上限" + MAX_WATCHLIST_SIZE + "只");
        }

        watchlistMapper.insertWatchlist(Watchlist.builder()
            .userId(userId)
            .code(code)
            .sortNo(count)
            .createTime(LocalDateTime.now())
            .build());
    }

    /**
     * 删除自选股
     * @param code 股票代码
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteWatchlist(String code) {
        code = normalizeCode(code);
        if (watchlistMapper.deleteByUserIdAndCode(UserContext.getUserId(), code) == 0) {
            throw new BusinessException("该股票不在自选中");
        }
    }

    /**
     * 更新自选股列表（覆盖并按顺序保存）
     * @param updateWatchlistDTO 自选股代码列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateWatchlist(UpdateWatchlistDTO updateWatchlistDTO) {
        List<String> codes = updateWatchlistDTO.getCodes();
        if (codes == null) {
            throw new BusinessException("自选股代码列表不能为空");
        }
        if (codes.size() > MAX_WATCHLIST_SIZE) {
            throw new BusinessException("自选股数量已达上限" + MAX_WATCHLIST_SIZE + "只");
        }

        List<String> normalizedCodes = new ArrayList<>(codes.size());
        Set<String> distinctCodes = new HashSet<>();
        for (String code : codes) {
            String normalizedCode = normalizeCode(code);
            if (!distinctCodes.add(normalizedCode)) {
                throw new BusinessException("自选股代码不能重复：" + normalizedCode);
            }
            normalizedCodes.add(normalizedCode);
        }
        validateStocksExist(normalizedCodes);

        Long userId = UserContext.getUserId();
        watchlistMapper.deleteByUserId(userId);
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < normalizedCodes.size(); i++) {
            watchlistMapper.insertWatchlist(Watchlist.builder()
                .userId(userId)
                .code(normalizedCodes.get(i))
                .sortNo(i)
                .createTime(now)
                .build());
        }
    }

    /**
     * 校验股票代码格式并去除首尾空白
     */
    private String normalizeCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new BusinessException("股票代码不能为空");
        }
        return code.trim();
    }

    /**
     * 校验股票代码是否全部存在
     */
    private void validateStocksExist(List<String> codes) {
        if (codes.isEmpty()) {
            return;
        }
        List<Stock> stocks = stockMapper.getStocksByCodes(codes);
        if (stocks == null || stocks.size() != codes.size()) {
            throw new BusinessException("存在不存在的股票代码");
        }
    }
}
