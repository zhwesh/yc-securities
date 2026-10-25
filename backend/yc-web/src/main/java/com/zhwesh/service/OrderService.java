package com.zhwesh.service;

import com.zhwesh.DTO.CancelOrderDTO;
import com.zhwesh.DTO.CreateOrderDTO;
import com.zhwesh.VO.OrderVO;
import com.zhwesh.engine.auction.AuctionMatch;
import com.zhwesh.engine.model.EngineOrder;
import com.zhwesh.entity.Stock;
import com.zhwesh.result.PageResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface OrderService {
    /**
     * 分页查询成交记录
     * @param accountId 资金账户id
     * @param code 股票代码
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param pageNum 页号
     * @param pageSize 每页大小
     * @return 分页查询结果
     */
    PageResult getTrades(Long accountId, String code, LocalDateTime startTime, LocalDateTime endTime, Integer pageNum, Integer pageSize);

    /**
     * 分页查询委托单
     * @param accountId 资金账户id
     * @param code 股票代码
     * @param status 委托单状态
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param pageNum 页号
     * @param pageSize 每页大小
     * @return 分页查询结果
     */
    PageResult getOrders(Long accountId, String code, Integer status, LocalDateTime startTime, LocalDateTime endTime, Integer pageNum, Integer pageSize);

    /**
     * 用户下单
     * @param createOrderDTO 所需信息
     * @return 委托单信息
     */
    OrderVO createOrder(CreateOrderDTO createOrderDTO);

    /**
     * 查询委托详情
     * @param id 委托单id
     * @return 委托单信息
     */
    OrderVO getOrderById(Long id);

    /**
     * 撤单
     * @param cancelOrderDTO 所需信息
     */
    void cancelOrder(CancelOrderDTO cancelOrderDTO);

    /**
     * 撤销全部未成交或部分成交委托
     */
    void cancelAllPendingOrders();

    /**
     * 撤销指定日期前未成交或部分成交委托
     * @param today 当前日期
     */
    void cancelPendingOrdersBefore(LocalDate today);

    /**
     * 结算集合竞价成交
     * @param matches 成交配对
     * @param stock 股票信息
     * @param tradeTime 成交时间
     */
    void settleAuction(List<AuctionMatch> matches, Stock stock, LocalDateTime tradeTime);

    /**
     * 释放集合竞价冻结委托
     * @param order 已冻结委托
     */
    void releasePendingOrder(EngineOrder order);

    /**
     * 撤销收盘集合竞价未成交委托
     * @param order 未成交委托
     */
    void cancelAuctionResidual(EngineOrder order);
}
