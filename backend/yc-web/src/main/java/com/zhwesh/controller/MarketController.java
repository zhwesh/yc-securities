package com.zhwesh.controller;

import com.zhwesh.VO.*;
import com.zhwesh.VO.StockTradeDetailVO;
import com.zhwesh.result.PageResult;
import com.zhwesh.result.Result;
import com.zhwesh.service.CallAuctionService;
import com.zhwesh.service.MarketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 行情相关接口
 */
@Slf4j
@Tag(name = "行情相关接口")
@RestController
@RequestMapping("/market")
public class MarketController {
    private final MarketService marketService;
    private final CallAuctionService callAuctionService;

    @Autowired
    public MarketController(MarketService marketService, CallAuctionService callAuctionService) {
        this.marketService = marketService;
        this.callAuctionService = callAuctionService;
    }

    /**
     * 查询个股最新行情
     * @param code 股票代码
     * @return 标准结果Result-StockQuoteVO（行情快照）
     */
    @GetMapping("/quote")
    @Operation(summary = "查询个股最新行情")
    public Result<StockQuoteVO> getQuote(@RequestParam("code") String code) {
        log.info("查询个股最新行情：{}", code);
        return Result.success(marketService.getQuote(code));
    }

    /**
     * 批量查询个股最新行情
     * @param codes 股票代码列表
     * @return 标准结果Result-List<StockQuoteVO>（行情快照列表）
     */
    @GetMapping("/quotes")
    @Operation(summary = "批量查询个股最新行情")
    public Result<List<StockQuoteVO>> getQuotes(@RequestParam("codes") List<String> codes) {
        log.info("批量查询个股最新行情：{}", codes);
        return Result.success(marketService.getQuotes(codes));
    }

    /**
     * 查询股票盘口
     * @param code 股票代码
     * @param depth 盘口档位数（默认5）
     * @return 标准结果Result-OrderBookVO（盘口）
     */
    @GetMapping("/order-book")
    @Operation(summary = "查询股票盘口")
    public Result<OrderBookVO> getOrderBook(
        @RequestParam("code") String code,
        @RequestParam(value = "depth", defaultValue = "5") Integer depth) {
        log.info("查询股票盘口：{}, 档位：{}", code, depth);
        return Result.success(marketService.getOrderBook(code, depth));
    }

    /**
     * 分页查询股票排行榜
     * @param sortBy 排序指标（默认 2=涨跌幅）
     * @param order 排序方向：1升序 2降序（默认 2）
     * @param pageNum 页码（默认 1）
     * @param pageSize 每页条数（默认 20）
     * @return 标准结果Result-PageResult（分页查询结果）
     */
    @GetMapping("/rank")
    @Operation(summary = "查询股票排行榜")
    public Result<PageResult> getRankPage(
        @RequestParam(value = "sortBy", defaultValue = "2") Integer sortBy,
        @RequestParam(value = "order", defaultValue = "2") Integer order,
        @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
        @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize
    ) {
        log.info("查询股票排行榜：{} {}", sortBy, order);
        return Result.success(marketService.getRankPage(sortBy, order, pageNum, pageSize));
    }

    /**
     * 查询股票某日分时数据
     * @param code 股票代码
     * @param date 日期（默认今天）
     * @return 标准结果Result-MinuteVO（分时数据）
     */
    @GetMapping("/minute")
    @Operation(summary = "查询股票某日分时数据")
    public Result<MinuteVO> getMinuteDatas(
        @RequestParam("code") String code,
        @RequestParam(value = "date", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        log.info("查询股票某日分时数据：{}, {}", code, date);
        return Result.success(marketService.getMinuteDatas(code, date));
    }

    /**
     * 查询逐笔成交
     * @param code 股票代码
     * @param limit 最近成交笔数（默认 50，最大 200）
     * @return 标准结果Result-List<StockTradeDetailVO>（逐笔成交列表）
     */
    @GetMapping("/trades")
    @Operation(summary = "查询逐笔成交")
    public Result<List<StockTradeDetailVO>> getTrades(
        @RequestParam("code") String code,
        @RequestParam(value = "limit", defaultValue = "50") Integer limit
    ) {
        log.info("查询逐笔成交：{}, {}", code, limit);
        return Result.success(marketService.getTrades(code, limit));
    }

    /**
     * 查询全部指数最新行情
     * @return 标准结果Result-List<IndexVO>（指数行情列表）
     */
    @GetMapping("/indexes")
    @Operation(summary = "查询指数行情")
    public Result<List<IndexVO>> getIndexes() {
        log.info("查询指数行情");
        return Result.success(marketService.getIndexes());
    }

    /**
     * 查询K线
     * @param code 股票代码
     * @param period 周期：1日K 2周K 3月K（默认 1）
     * @param limit 返回条数（默认 200）
     * @param endDate 向前翻页游标（不含），不传返回最新
     * @param adjust 复权方式：0不复权 1前复权 2后复权（默认 1；当前数据为不复权，参数暂未生效）
     * @return 标准结果Result-KLineVO（K线数据）
     */
    @GetMapping("/kline")
    @Operation(summary = "查询K线")
    public Result<KLineVO> getKLine(
        @RequestParam("code") String code,
        @RequestParam(value = "period", defaultValue = "1") Integer period,
        @RequestParam(value = "limit", defaultValue = "200") Integer limit,
        @RequestParam(value = "endDate", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        @RequestParam(value = "adjust", defaultValue = "1") Integer adjust
    ) {
        log.info("查询K线：{}, {}, {}, {}", code, period, limit, endDate);
        return Result.success(marketService.getKLine(code, period, limit, endDate, adjust));
    }

    /**
     * 查询分钟级K线
     * @param code 股票代码
     * @param period 周期（分钟）：1/5/15/30/60，默认 5
     * @param limit 返回条数（默认 240，最大 500）
     * @param endTime 游标（不含），null 表示最新
     * @return 标准结果Result-MinuteKLineVO（分钟K线数据）
     */
    @GetMapping("/minute-kline")
    @Operation(summary = "查询分钟级K线")
    public Result<MinuteKLineVO> getMinuteKLine(
        @RequestParam("code") String code,
        @RequestParam(value = "period", defaultValue = "5") Integer period,
        @RequestParam(value = "limit", defaultValue = "240") Integer limit,
        @RequestParam(value = "endTime", required = false)
        @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss",
            fallbackPatterns = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime
    ) {
        log.info("查询分钟K线：{}, {}, {}, {}", code, period, limit, endTime);
        return Result.success(marketService.getMinuteKLine(code, period, limit, endTime));
    }

    /**
     * 查询集合竞价快照
     * @param code 股票代码
     * @return 标准结果Result-CallAuctionVO（集合竞价快照）
     */
    @GetMapping("/auction")
    @Operation(summary = "查询集合竞价")
    public Result<CallAuctionVO> getCallAuction(@RequestParam("code") String code) {
        log.info("查询集合竞价：{}", code);
        return Result.success(callAuctionService.getSnapshot(code));
    }
}
