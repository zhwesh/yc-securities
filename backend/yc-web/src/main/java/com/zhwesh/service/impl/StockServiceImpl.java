package com.zhwesh.service.impl;

import com.zhwesh.VO.StockDetailVO;
import com.zhwesh.VO.StockVO;
import com.zhwesh.entity.Stock;
import com.zhwesh.exception.BusinessException;
import com.zhwesh.mapper.StockMapper;
import com.zhwesh.result.PageResult;
import com.zhwesh.service.StockService;
import com.zhwesh.util.PageUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class StockServiceImpl implements StockService {
    private final StockMapper stockMapper;

    @Autowired
    public StockServiceImpl(StockMapper stockMapper) {
        this.stockMapper = stockMapper;
    }

    /**
     * 根据代码查询股票详情
     * @param code 股票代码
     * @return 股票详细信息
     */
    @Override
    public StockDetailVO getStockByCode(String code) {
        Stock stock = stockMapper.getStockByCode(code);
        if (stock == null) {
            throw new BusinessException("股票不存在");
        }
        StockDetailVO stockDetailVO = new StockDetailVO();
        BeanUtils.copyProperties(stock, stockDetailVO);
        return stockDetailVO;
    }

    /**
     * 条件分页查询
     * @param keyword 关键字（名称/代码）
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    @Override
    public PageResult getStocks(String keyword, Integer pageNum, Integer pageSize) {
        keyword = keyword == null ? "" : keyword;
        int offset = PageUtil.getOffset(pageNum, pageSize);
        int size = PageUtil.getPageSize(pageSize);

        long total = stockMapper.countStocks(keyword);
        List<Stock> stocks = stockMapper.getStocks(keyword, offset, size);
        List<StockVO> stockVOs = new ArrayList<>();
        for (Stock stock : stocks) {
            StockVO stockVO = new StockVO();
            BeanUtils.copyProperties(stock, stockVO);
            stockVOs.add(stockVO);
        }
        return new PageResult(total, stockVOs);
    }
}
