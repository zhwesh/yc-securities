package com.zhwesh.controller;

import com.zhwesh.DTO.CancelOrderDTO;
import com.zhwesh.DTO.CreateOrderDTO;
import com.zhwesh.VO.OrderVO;
import com.zhwesh.result.PageResult;
import com.zhwesh.result.Result;
import com.zhwesh.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 订单相关接口
 */
@Slf4j
@Tag(name = "订单相关接口")
@RestController
public class OrderController {
    private final OrderService orderService;

    @Autowired
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * 分页查询成交记录
     * @param accountId 资金账户id
     * @param code 股票代码
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param pageNum 页号
     * @param pageSize 每页大小
     * @return 标准结果Result-PageResult（分页查询结果）
     */
    @GetMapping("/trade")
    @Operation(summary = "分页查询成交记录")
    public Result<PageResult> getTrades(
        @RequestParam("accountId") Long accountId,
        @RequestParam(value = "code", required = false) String code,
        @RequestParam(value = "startTime", required = false)
        @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss",
            fallbackPatterns = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
        @RequestParam(value = "endTime", required = false)
        @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss",
            fallbackPatterns = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
        @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
        @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize
    ) {
        log.info("分页查询成交记录：{}, {}", accountId, code);
        return Result.success(orderService.getTrades(
            accountId, code, startTime, endTime,
            pageNum, pageSize
        ));
    }

    /**
     * 分页查询委托单
     * @param accountId 资金账户id
     * @param code 股票代码
     * @param status 委托单状态
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param pageNum 页号
     * @param pageSize 每页大小
     * @return 标准结果Result-PageResult（分页查询结果）
     */
    @GetMapping("/order")
    @Operation(summary = "分页查询委托单")
    public Result<PageResult> getOrders(
        @RequestParam("accountId") Long accountId,
        @RequestParam(value = "code", required = false) String code,
        @RequestParam(value = "status", required = false) Integer status,
        @RequestParam(value = "startTime", required = false)
        @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss",
            fallbackPatterns = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
        @RequestParam(value = "endTime", required = false)
        @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss",
            fallbackPatterns = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
        @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
        @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize
    ) {
        log.info("分页查询委托单：{}, {}", accountId, code);
        return Result.success(orderService.getOrders(
            accountId, code, status, startTime, endTime,
            pageNum, pageSize
        ));
    }

    /**
     * 用户下单
     * @param createOrderDTO 所需信息
     * @return 标准结果Result-OrderVO（委托单信息）
     */
    @PostMapping("/order")
    @Operation(summary = "用户下单")
    public Result<OrderVO> createOrder(@RequestBody CreateOrderDTO createOrderDTO) {
        log.info("用户下单：{}", createOrderDTO);
        return Result.success(orderService.createOrder(createOrderDTO));
    }

    /**
     * 查询委托详情
     * @param id 委托单id
     * @return 标准结果Result-OrderVO（委托单信息）
     */
    @GetMapping("/order/{id}")
    @Operation(summary = "查询委托详情")
    public Result<OrderVO> getOrderById(@PathVariable("id") Long id) {
        log.info("查询委托详情：{}", id);
        return Result.success(orderService.getOrderById(id));
    }

    /**
     * 撤单
     * @param cancelOrderDTO 所需信息
     * @return 标准结果Result（无数据）
     */
    @PutMapping("/order/cancel")
    @Operation(summary = "撤单")
    public Result<Void> cancelOrder(@RequestBody CancelOrderDTO cancelOrderDTO) {
        log.info("撤单：{}", cancelOrderDTO);
        orderService.cancelOrder(cancelOrderDTO);
        return Result.success();
    }
}
