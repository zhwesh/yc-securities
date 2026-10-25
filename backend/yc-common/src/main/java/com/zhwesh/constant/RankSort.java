package com.zhwesh.constant;

import java.util.Set;

/**
 * 股票排行榜排序指标
 */
public class RankSort {
    public static final int PRICE = 1;
    public static final int CHANGE_PERCENT = 2;
    public static final int VOLUME = 3;
    public static final int AMOUNT = 4;
    public static final int TURNOVER_RATE = 5;
    public static final int PB = 6;
    public static final int MARKET_VALUE = 7;
    public static final int CIRCULATE_MARKET_VALUE = 8;
    public static final int BOOK_VALUE = 9;

    // 合法排序指标
    public static final Set<Integer> validSort =
        Set.of(PRICE, CHANGE_PERCENT, VOLUME, AMOUNT, TURNOVER_RATE,
            PB, MARKET_VALUE, CIRCULATE_MARKET_VALUE, BOOK_VALUE);
}
