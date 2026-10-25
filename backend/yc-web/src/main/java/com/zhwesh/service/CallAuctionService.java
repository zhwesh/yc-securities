package com.zhwesh.service;

import com.zhwesh.VO.CallAuctionVO;

import java.time.LocalDateTime;

public interface CallAuctionService {
    /**
     * 查询集合竞价快照
     * @param code 股票代码
     * @return 集合竞价快照
     */
    CallAuctionVO getSnapshot(String code);

    /**
     * 关闭开盘集合竞价并结算成交
     * @param closeTime 竞价结束时间
     */
    void closeOpeningAuction(LocalDateTime closeTime);

    /**
     * 9:30 释放竞价未成交/冻结委托到连续交易
     */
    void releaseOpeningOrders();

    /**
     * 14:57 将连续交易未成交委托转入收盘集合竞价
     */
    void prepareClosingAuction();

    /**
     * 15:00 关闭收盘集合竞价，成交并撤销未成交委托
     * @param closeTime 竞价结束时间
     */
    void closeClosingAuction(LocalDateTime closeTime);

    /**
     * 跨日清理
     */
    void clearDay();
}
