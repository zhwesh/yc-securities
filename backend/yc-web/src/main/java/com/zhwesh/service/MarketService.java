package com.zhwesh.service;

import com.zhwesh.VO.*;
import com.zhwesh.VO.StockTradeDetailVO;
import com.zhwesh.result.PageResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface MarketService {
    /**
     * 查询个股最新行情快照
     * @param code 股票代码
     * @return 行情快照
     */
    StockQuoteVO getQuote(String code);

    /**
     * 批量查询个股最新行情
     * @param codes 股票代码
     * @return 行情快照列表
     */
    List<StockQuoteVO> getQuotes(List<String> codes);

    /**
     * 查询股票盘口
     * @param code 股票代码
     * @param depth 盘口档位数
     * @return 盘口
     */
    OrderBookVO getOrderBook(String code, Integer depth);

    /**
     * 分页查询股票排行榜
     * @param sortBy 排序指标
     * @param order 排序方向
     * @param pageNum 页号
     * @param pageSize 每页大小
     * @return 分页查询结果
     */
    PageResult getRankPage(Integer sortBy, Integer order, Integer pageNum, Integer pageSize);

    /**
     * 加载每只股票最近5个交易日的日均成交量（股）
     * @param beforeDate 截止日期（不含）
     * @return 股票代码 -> 日均成交量
     */
    Map<String, Long> loadAvgDailyVolumes(LocalDate beforeDate);

    /**
     * 查询股票某日分时数据
     * @param code 股票代码
     * @param date 日期
     * @return 分时数据
     */
    MinuteVO getMinuteDatas(String code, LocalDate date);

    /**
     * 查询逐笔成交明细
     * @param code 股票代码
     * @param limit 查询数量
     * @return 成交明细列表
     */
    List<StockTradeDetailVO> getTrades(String code, Integer limit);

    /**
     * 查询全部指数最新行情
     * @return 指数行情列表
     */
    List<IndexVO> getIndexes();

    /**
     * 查询K线
     * @param code 股票代码
     * @param period 周期
     * @param limit 查询条数
     * @param endDate 截止日期（查询此日期以前的数据）
     * @param adjust 复权方式
     * @return K线图信息
     */
    KLineVO getKLine(String code, Integer period, Integer limit, LocalDate endDate,
        Integer adjust);

    /**
     * 查询分钟级K线
     * @param code 股票代码
     * @param period 周期（分钟，支持 1/5/15/30/60）
     * @param limit 返回条数
     * @param endTime 游标（不含），null 表示最新
     * @return 分钟K线
     */
    MinuteKLineVO getMinuteKLine(String code, Integer period, Integer limit,
        LocalDateTime endTime);
}
