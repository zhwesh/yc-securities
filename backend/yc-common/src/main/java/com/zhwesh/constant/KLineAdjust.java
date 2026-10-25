package com.zhwesh.constant;

import java.util.Set;

/**
 * K线复权方式
 */
public class KLineAdjust {
    public static final int NONE = 0;
    public static final int QFQ = 1;
    public static final int HFQ = 2;

    // 合法复权方式
    public static final Set<Integer> validAdjust = Set.of(NONE, QFQ, HFQ);
}
