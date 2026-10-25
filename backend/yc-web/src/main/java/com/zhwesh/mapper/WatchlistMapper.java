package com.zhwesh.mapper;

import com.zhwesh.VO.StockVO;
import com.zhwesh.entity.Watchlist;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WatchlistMapper {
    /**
     * 查询用户自选股（按排序号升序）
     * @param userId 用户id
     * @return 自选股票列表（代码+名称）
     */
    @Select("SELECT w.code, s.name FROM watchlist w " +
        "JOIN stock s ON s.code = w.code " +
        "WHERE w.user_id = #{userId} " +
        "ORDER BY w.sort_no, w.id")
    List<StockVO> getStocksByUserId(Long userId);

    /**
     * 查询用户指定自选股
     * @param userId 用户id
     * @param code 股票代码
     * @return 自选股记录
     */
    @Select("SELECT * FROM watchlist WHERE user_id = #{userId} AND code = #{code}")
    Watchlist getByUserIdAndCode(@Param("userId") Long userId, @Param("code") String code);

    /**
     * 统计用户自选股数量
     * @param userId 用户id
     * @return 自选股数量
     */
    @Select("SELECT COUNT(*) FROM watchlist WHERE user_id = #{userId}")
    int countByUserId(Long userId);

    /**
     * 新增自选股
     * @param watchlist 自选股信息
     */
    @Options(useGeneratedKeys = true, keyProperty = "id")
    @Insert("INSERT INTO watchlist(user_id, code, sort_no, create_time) " +
        "VALUES(#{userId}, #{code}, #{sortNo}, #{createTime})")
    void insertWatchlist(Watchlist watchlist);

    /**
     * 删除用户指定自选股
     * @param userId 用户id
     * @param code 股票代码
     * @return 受影响行数
     */
    @Delete("DELETE FROM watchlist WHERE user_id = #{userId} AND code = #{code}")
    int deleteByUserIdAndCode(@Param("userId") Long userId, @Param("code") String code);

    /**
     * 删除用户全部自选股
     * @param userId 用户id
     * @return 受影响行数
     */
    @Delete("DELETE FROM watchlist WHERE user_id = #{userId}")
    int deleteByUserId(Long userId);
}
