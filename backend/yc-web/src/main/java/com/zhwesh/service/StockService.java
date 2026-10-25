package com.zhwesh.service;

import com.zhwesh.VO.StockDetailVO;
import com.zhwesh.result.PageResult;

public interface StockService {
    /**
     * 根据代码查询股票详情
     * @param code 股票代码
     * @return 股票详细信息
     */
    StockDetailVO getStockByCode(String code);

    /**
     * 条件分页查询
     * @param keyword 关键字（名称/代码）
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 分页结果（总数 + 股票列表）
     */
    PageResult getStocks(String keyword, Integer pageNum, Integer pageSize);
}
