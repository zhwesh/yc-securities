package com.zhwesh.constant;

import java.util.Set;

/**
 * 排行榜排序方向
 */
public class RankOrder {
    public static final int ASC = 1;
    public static final int DESC = 2;

    // 合法排序方向
    public static final Set<Integer> validOrder = Set.of(ASC, DESC);
}
