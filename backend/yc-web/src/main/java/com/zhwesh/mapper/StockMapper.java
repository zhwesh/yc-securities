package com.zhwesh.mapper;

import com.zhwesh.entity.Stock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StockMapper {
    /**
     * 根据代码查询股票
     * @param code 股票代码
     * @return 股票信息
     */
    @Select("SELECT * FROM stock WHERE code = #{code}")
    Stock getStockByCode(String code);

    /**
     * 根据代码列表批量查询股票
     * @param codes 股票代码列表
     * @return 股票信息列表
     */
    @Select("<script>" +
        "SELECT * FROM stock WHERE code IN " +
        "<foreach collection='codes' item='code' open='(' separator=',' close=')'>#{code}</foreach>" +
        "</script>")
    List<Stock> getStocksByCodes(@Param("codes") List<String> codes);

    /**
     * 根据keyword进行分页查询
     * @param keyword 关键字（名称/代码）
     * @param offset 偏移量
     * @param pageSize 每页大小
     * @return 股票列表
     */
    @Select("SELECT * FROM stock " +
        "WHERE code LIKE CONCAT(#{keyword}, '%') " +
        "OR name LIKE CONCAT(#{keyword}, '%') " +
        "ORDER BY code " +
        "LIMIT #{offset}, #{pageSize}")
    List<Stock> getStocks(@Param("keyword") String keyword,
        @Param("offset") int offset,
        @Param("pageSize") int pageSize);

    /**
     * 根据keyword统计股票数量
     * @param keyword 关键字（名称/代码）
     * @return 总记录数
     */
    @Select("SELECT COUNT(*) FROM stock " +
        "WHERE code LIKE CONCAT(#{keyword}, '%') " +
        "OR name LIKE CONCAT(#{keyword}, '%')")
    long countStocks(String keyword);

    /**
     * 查询全部股票（行情引擎初始化用）
     * @return 股票列表
     */
    @Select("SELECT * FROM stock ORDER BY code")
    List<Stock> getAllStocks();
}
