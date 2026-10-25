package com.zhwesh.util;

/**
 * 分页工具类
 */
public class PageUtil {

    private PageUtil() {
    }

    /**
     * 默认每页条数
     */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /**
     * 每页条数上限
     */
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * 计算LIMIT偏移量（pageNum 从 1 开始，非法值自动修正）
     */
    public static int getOffset(Integer pageNum, Integer pageSize) {
        return (getPageNum(pageNum) - 1) * getPageSize(pageSize);
    }

    /**
     * 规范化页码（最小 1）
     */
    public static int getPageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    /**
     * 规范化每页条数（默认 10，最大 100）
     */
    public static int getPageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
