package com.zhwesh.constant;

import java.util.Set;

/**
 * K线周期
 */
public class KLinePeriod {
    public static final int DAY = 1;
    public static final int WEEK = 2;
    public static final int MONTH = 3;
    public static final int QUARTER = 4;
    public static final int YEAR = 5;

    // 合法周期
    public static final Set<Integer> validPeriod = Set.of(DAY, WEEK, MONTH, QUARTER, YEAR);
}
