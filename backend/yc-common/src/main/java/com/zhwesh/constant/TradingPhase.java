package com.zhwesh.constant;

/**
 * A股交易时段
 */
public enum TradingPhase {
    CLOSED(0, "休市"),
    OPEN_AUCTION_CANCELABLE(1, "开盘集合竞价（可撤单）"),
    OPEN_AUCTION_LOCKED(2, "开盘集合竞价（不可撤单）"),
    OPEN_AUCTION_FREEZE(3, "开盘竞价冻结"),
    CONTINUOUS(4, "连续交易"),
    CLOSE_AUCTION(5, "收盘集合竞价");

    private final int code;
    private final String label;

    TradingPhase(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 是否处于集合竞价阶段
     */
    public boolean isAuction() {
        return this == OPEN_AUCTION_CANCELABLE
            || this == OPEN_AUCTION_LOCKED
            || this == OPEN_AUCTION_FREEZE
            || this == CLOSE_AUCTION;
    }

    /**
     * 是否允许撤销集合竞价委托
     */
    public boolean canCancelAuctionOrder() {
        return this == OPEN_AUCTION_CANCELABLE;
    }
}
