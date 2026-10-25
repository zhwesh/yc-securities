package com.zhwesh.mapper;

import com.zhwesh.entity.DailyQuote;
import com.zhwesh.entity.MinuteQuote;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface QuoteMapper {
    /**
     * 批量写入/更新分钟行情（依赖minute_quote唯一索引uk_stock_time）
     * @param list 分钟行情列表
     * @return 影响行数
     */
    int batchUpsertMinutes(@Param("list") List<MinuteQuote> list);

    /**
     * 批量写入/更新日行情（依赖daily_quote唯一索引uk_stock_date）
     * @param list 日行情列表
     * @return 影响行数
     */
    int batchUpsertDailies(@Param("list") List<DailyQuote> list);

    /**
     * 查询某日全部分钟行情（重启恢复用）
     * @param date 交易日
     * @return 分钟行情列表（按股票、时间升序）
     */
    List<MinuteQuote> getMinutesByDate(@Param("date") LocalDate date);

    /**
     * 查询某只股票某日的分钟行情（分时接口用）
     * @param code 股票代码
     * @param date 交易日
     * @return 分钟行情列表
     */
    List<MinuteQuote> getMinutesByCodeAndDate(
        @Param("code") String code,
        @Param("date") LocalDate date);

    /**
     * 查询某日全部日行情（重启恢复用）
     * @param date 交易日
     * @return 日行情列表
     */
    List<DailyQuote> getDailiesByDate(@Param("date") LocalDate date);

    /**
     * 查询每只股票最近一条日行情（重启恢复昨收/最新价用）
     * @return 日行情列表
     */
    List<DailyQuote> getLatestDailies();

    /**
     * 查询某只股票早于指定日期的最近N条日线
     * @param code 股票代码
     * @param endDate 截止日期（不含，null 表示最新）
     * @param limit 条数
     * @return 日行情列表
     */
    List<DailyQuote> getDailiesBefore(@Param("code") String code,
        @Param("endDate") LocalDate endDate, @Param("limit") int limit);

    /**
     * 查询某只股票全部日线（周/月K聚合用，按日期升序）
     * @param code 股票代码
     * @return 日行情列表
     */
    List<DailyQuote> getDailiesByCode(@Param("code") String code);

    /**
     * 查询某只股票某日之前的最近N条日线（量比计算用，按日期倒序）
     * @param code 股票代码
     * @param beforeDate 截止日期（不含）
     * @param limit 条数
     * @return 日行情列表
     */
    List<DailyQuote> getRecentDailies(@Param("code") String code,
        @Param("beforeDate") LocalDate beforeDate, @Param("limit") int limit);

    /**
     * 查询最近 N 个交易日的全市场成交量（计算日均成交量用）
     * @param beforeDate 截止日期（不含）
     * @param days 交易日数
     * @return 日行情列表（仅 code/date/volume）
     */
    List<DailyQuote> getRecentDailyVolumes(@Param("beforeDate") LocalDate beforeDate,
        @Param("days") int days);

    /**
     * 查询某只股票某日的一条日行情（分时接口取昨收用）
     * @param code 股票代码
     * @param date 交易日
     * @return 日行情（无数据返回 null）
     */
    DailyQuote getDailyByCodeAndDate(@Param("code") String code,
        @Param("date") LocalDate date);

    /**
     * 查询最新有日线数据的交易日（周/月/季/年K聚合用）
     * @return 最新交易日，无数据时返回 null
     */
    @Select("SELECT MAX(date) FROM daily_quote")
    LocalDate getLatestDailyDate();

    /**
     * 查询某只股票截止时间之前最近 N 条分钟线（分钟K线聚合用）
     * @param code 股票代码
     * @param endTime 截止时间（不含），null 表示最新
     * @param limit 条数
     * @return 分钟线（按时间倒序）
     */
    List<MinuteQuote> getMinutesBefore(@Param("code") String code,
        @Param("endTime") LocalDateTime endTime,
        @Param("limit") int limit);
}
