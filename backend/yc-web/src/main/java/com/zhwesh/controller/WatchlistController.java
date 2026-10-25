package com.zhwesh.controller;

import com.zhwesh.DTO.AddWatchlistDTO;
import com.zhwesh.DTO.UpdateWatchlistDTO;
import com.zhwesh.VO.StockVO;
import com.zhwesh.result.Result;
import com.zhwesh.service.WatchlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 自选股相关接口
 */
@Slf4j
@Tag(name = "自选股相关接口")
@RestController
@RequestMapping("/watchlist")
public class WatchlistController {
    private final WatchlistService watchlistService;

    @Autowired
    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    /**
     * 查询当前用户自选股
     * @return 标准结果Result-List<StockVO>（自选股票列表）
     */
    @GetMapping
    @Operation(summary = "查询自选股列表")
    public Result<List<StockVO>> getWatchlist() {
        log.info("查询自选股列表");
        return Result.success(watchlistService.getWatchlist());
    }

    /**
     * 添加自选股
     * @param addWatchlistDTO 自选股信息
     * @return 标准结果Result-Void
     */
    @PostMapping
    @Operation(summary = "添加自选股")
    public Result<Void> addWatchlist(@RequestBody AddWatchlistDTO addWatchlistDTO) {
        log.info("添加自选股：{}", addWatchlistDTO);
        watchlistService.addWatchlist(addWatchlistDTO);
        return Result.success();
    }

    /**
     * 删除自选股
     * @param code 股票代码
     * @return 标准结果Result-Void
     */
    @DeleteMapping
    @Operation(summary = "删除自选股")
    public Result<Void> deleteWatchlist(@RequestParam("code") String code) {
        log.info("删除自选股：{}", code);
        watchlistService.deleteWatchlist(code);
        return Result.success();
    }

    /**
     * 更新自选股列表（覆盖并按顺序保存，可用于拖拽排序）
     * @param updateWatchlistDTO 自选股代码列表
     * @return 标准结果Result-Void
     */
    @PutMapping
    @Operation(summary = "更新自选股排序")
    public Result<Void> updateWatchlist(@RequestBody UpdateWatchlistDTO updateWatchlistDTO) {
        log.info("更新自选股排序：{}", updateWatchlistDTO);
        watchlistService.updateWatchlist(updateWatchlistDTO);
        return Result.success();
    }
}
