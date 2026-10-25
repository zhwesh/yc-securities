package com.zhwesh.controller;

import com.zhwesh.VO.PositionVO;
import com.zhwesh.result.Result;
import com.zhwesh.service.PositionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 持仓相关接口
 */
@Slf4j
@Tag(name = "持仓相关接口")
@RestController
@RequestMapping("/position")
public class PositionController {
    private final PositionService positionService;

    @Autowired
    public PositionController(PositionService positionService) {
        this.positionService = positionService;
    }

    /**
     * 查询持仓列表
     * @param accountId 资金账户id
     * @return 标准结果Result-List<PositionVO>（持仓列表）
     */
    @GetMapping
    @Operation(summary = "查询持仓列表")
    public Result<List<PositionVO>> getPositions(@RequestParam("accountId") Long accountId) {
        log.info("查询持仓列表：{}", accountId);
        return Result.success(positionService.getPositions(accountId));
    }
}
