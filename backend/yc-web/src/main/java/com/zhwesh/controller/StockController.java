package com.zhwesh.controller;

import com.zhwesh.VO.StockDetailVO;
import com.zhwesh.result.PageResult;
import com.zhwesh.result.Result;
import com.zhwesh.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 股票相关接口
 */
@Slf4j
@Tag(name = "股票相关接口")
@RestController
@RequestMapping("/stock")
public class StockController {
    private final StockService stockService;

    @Autowired
    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    /**
     * 根据代码查询股票详情
     * @param code 股票代码
     * @return 标准结果Result-StockDetailVO（股票详细信息）
     */
    @GetMapping("/{code}")
    @Operation(summary = "根据代码查询股票详情")
    public Result<StockDetailVO> getStockByCode(@PathVariable("code") String code) {
        log.info("根据代码查询股票详情：{}", code);
        return Result.success(stockService.getStockByCode(code));
    }

    /**
     * 条件分页查询
     * @param keyword 关键字（名称/代码）
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 标准结果Result-PageResult（总数 + 股票列表）
     */
    @GetMapping
    @Operation(summary = "条件分页查询")
    public Result<PageResult> getStocks(
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
        @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize
    ) {
        log.info("条件分页查询：{}", keyword);
        return Result.success(stockService.getStocks(keyword, pageNum, pageSize));
    }
}
