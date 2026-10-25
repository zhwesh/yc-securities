package com.zhwesh.util;

import com.zhwesh.constant.Board;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 股票工具类
 */
public class StockUtil {
    /**
     * 根据股票代码判断所属板块
     */
    public static String getBoardByCode(String code) {
        if (code.startsWith("688") || code.startsWith("689")) {
            return Board.SSE_STAR_MARKET;
        }
        if (code.startsWith("300") || code.startsWith("301")) {
            return Board.CHINEXT_MARKET;
        }
        if (code.startsWith("8") || code.startsWith("4") || code.startsWith("92")) {
            return Board.BSE;
        }
        return Board.MAIN_BOARD;
    }

    /**
     * 获取板块涨跌幅限制（小数）
     */
    public static BigDecimal getLimitPercent(String board) {
        if (Board.CHINEXT_MARKET.equals(board) || Board.SSE_STAR_MARKET.equals(board)) {
            return new BigDecimal("0.2");
        }
        if (Board.BSE.equals(board)) {
            return new BigDecimal("0.3");
        }
        return new BigDecimal("0.1");
    }

    /**
     * 获取板块最小买入数量（股）
     */
    public static int getMinQuantity(String board) {
        if (Board.SSE_STAR_MARKET.equals(board)) {
            return 200;
        }
        return 100;
    }

    /**
     * 解析账户可交易板块字符串
     */
    public static List<String> parseBoards(String boards) {
        if (boards == null || boards.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(boards.split(",")));
    }

    /**
     * 判断账户是否可交易指定板块
     */
    public static boolean supportsBoard(String boards, String board) {
        return parseBoards(boards).contains(board);
    }

    /**
     * 获取板块开通门槛（元，按可用资产判定）
     * 科创板、北交所50万，创业板10万，主板默认开通
     */
    public static BigDecimal getOpenThreshold(String board) {
        if (Board.SSE_STAR_MARKET.equals(board) || "北交所".equals(board)) {
            return new BigDecimal("500000");
        }
        if (Board.CHINEXT_MARKET.equals(board)) {
            return new BigDecimal("100000");
        }
        return BigDecimal.ZERO;
    }
}
