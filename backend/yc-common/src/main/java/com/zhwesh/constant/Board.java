package com.zhwesh.constant;

import java.util.HashSet;
import java.util.List;

/**
 * 所有板块名称
 */
public class Board {
    public static String MAIN_BOARD = "主板";
    public static String SSE_STAR_MARKET = "科创板";
    public static String CHINEXT_MARKET = "创业板";
    public static String BSE = "北交所";

    // 合法板块名
    public static HashSet<String> validBoardName = new HashSet<>(
        List.of(SSE_STAR_MARKET, CHINEXT_MARKET, BSE)
    );
}
