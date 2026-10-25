package com.zhwesh.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

import com.zhwesh.entity.PeriodQuote;

@Mapper
public interface PeriodQuoteMapper {
    /**
     * 按周期聚合源表并写入对应的周/月/季/年K表（幂等 upsert）
     * 注意：targetTable/sourceTable 只能传内部常量表名，禁止传入外部输入
     * @param targetTable 目标表名（week_quote/month_quote/quarter_quote/year_quote）
     * @param sourceTable 源表名（daily_quote/month_quote/quarter_quote）
     * @param periodKey 周期标识（如 2026-W43、2026-10、2026-Q4、2026）
     * @param startDate 周期开始日期（含）
     * @param endDate 周期结束日期（含）
     * @return 受影响行数
     */
    int upsertPeriodQuote(@Param("targetTable") String targetTable,
        @Param("sourceTable") String sourceTable,
        @Param("periodKey") String periodKey,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);

    /**
     * 统计指定周期是否已聚合（补全季/年数据前检查）
     * @param table 表名（内部常量）
     * @param periodKey 周期标识
     * @return 行数
     */
    int countByPeriodKey(@Param("table") String table, @Param("periodKey") String periodKey);

    /**
     * 查询历史周期K线（按日期倒序）
     * @param table 表名（内部常量）
     * @param code 股票代码
     * @param beforeDate 截止日期（不含），null 表示不限
     * @param limit 返回条数
     * @return 周期K线
     */
    List<PeriodQuote> getPeriodQuotes(@Param("table") String table,
        @Param("code") String code,
        @Param("beforeDate") LocalDate beforeDate,
        @Param("limit") int limit);
}
