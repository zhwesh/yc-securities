package com.zhwesh.service;

import com.zhwesh.DTO.AddWatchlistDTO;
import com.zhwesh.DTO.UpdateWatchlistDTO;
import com.zhwesh.VO.StockVO;

import java.util.List;

public interface WatchlistService {
    /**
     * 查询当前用户自选股
     * @return 自选股票列表（代码+名称）
     */
    List<StockVO> getWatchlist();

    /**
     * 添加自选股
     * @param addWatchlistDTO 自选股信息
     */
    void addWatchlist(AddWatchlistDTO addWatchlistDTO);

    /**
     * 删除自选股
     * @param code 股票代码
     */
    void deleteWatchlist(String code);

    /**
     * 更新自选股列表（覆盖并按顺序保存）
     * @param updateWatchlistDTO 自选股代码列表
     */
    void updateWatchlist(UpdateWatchlistDTO updateWatchlistDTO);
}
