package com.zhwesh.mapper;

import com.zhwesh.entity.Order;
import com.zhwesh.entity.SoldOrder;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderMapper {
    /**
     * 查询成交记录
     * @param accountId 资金账户id
     * @param stockCode 股票代码
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param offset 起始位置
     * @param limit 查询数量
     * @return 成交记录列表
     */
    List<SoldOrder> getSoldOrders(
        @Param("accountId") Long accountId, @Param("stockCode") String stockCode,
        @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime,
        @Param("offset") int offset, @Param("limit") int limit
    );

    /**
     * 统计成交记录数
     * @param accountId 资金账户id
     * @param stockCode 股票代码
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 记录数
     */
    long getSoldOrdersCount(
        @Param("accountId") Long accountId, @Param("stockCode") String stockCode,
        @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime
    );

    /**
     * 查询委托单
     * @param accountId 资金账户id
     * @param code 股票代码
     * @param status 委托单状态
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param offset 起始位置
     * @param limit 查询数量
     * @return 委托单列表
     */
    List<Order> getOrders(
        @Param("accountId") Long accountId, @Param("code") String code,
        @Param("status") Integer status,
        @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime,
        @Param("offset") int offset, @Param("limit") int limit);

    /**
     * 统计委托单数
     * @param accountId 资金账户id
     * @param code 股票代码
     * @param status 委托单状态
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 记录数
     */
    long getOrdersCount(
        @Param("accountId") Long accountId, @Param("code") String code,
        @Param("status") Integer status,
        @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    /**
     * 按幂等键查询委托单
     * @param orderKey UUID幂等键
     * @return 委托单
     */
    @Select("SELECT * FROM orders WHERE order_key = #{orderKey}")
    Order getOrderByOrderKey(@Param("orderKey") String orderKey);

    /**
     * 按id查询委托单
     * @param id 委托单id
     * @return 委托单
     */
    @Select("SELECT * FROM orders WHERE id = #{id}")
    Order getOrderById(@Param("id") Long id);

    /**
     * 查询全部未成交或部分成交委托
     * @return 委托单列表
     */
    @Select("SELECT * FROM orders WHERE status IN (1, 2)")
    List<Order> getActiveOrders();

    /**
     * 查询指定日期前未成交或部分成交委托
     * @param today 当前日期
     * @return 委托单列表
     */
    @Select("SELECT * FROM orders WHERE status IN (1, 2) AND create_time < #{today}")
    List<Order> getActiveOrdersBefore(@Param("today") LocalDate today);

    /**
     * 更新未成交或部分成交委托状态
     * @param id 委托单id
     * @param status 委托单状态
     * @return 受影响行数
     */
    @Update("UPDATE orders SET status = #{status} WHERE id = #{id} AND status IN (1, 2)")
    int updateStatusIfActive(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 增加已成交数量并更新状态
     * @param id 委托单id
     * @param quantity 本次成交数量
     */
    @Update("UPDATE orders SET sold_quantity = sold_quantity + #{quantity}, " +
        "status = IF(sold_quantity + #{quantity} >= ABS(quantity), 3, 2) WHERE id = #{id}")
    void increaseSoldQuantity(@Param("id") Long id, @Param("quantity") int quantity);

    /**
     * 批量写入成交记录
     * @param soldOrders 成交记录列表
     */
    void batchInsertSoldOrders(@Param("list") List<SoldOrder> soldOrders);

    /**
     * 查询股票逐笔成交
     * @param stockCode 股票代码
     * @param limit 查询数量
     * @return 成交列表
     */
    List<SoldOrder> getMarketTrades(
        @Param("stockCode") String stockCode, @Param("limit") int limit);

    /**
     * 创建委托单
     * @param order 委托单
     */
    @Insert("INSERT INTO orders(order_key, account_id, stock_code, price, freeze_price, quantity, " +
        "sold_quantity, type, status, create_time)" +
        "VALUES(#{orderKey}, #{accountId}, #{stockCode}, #{price}, #{freezePrice}, #{quantity}, " +
        "#{soldQuantity}, #{type}, #{status}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    void insertOrder(Order order);
}
