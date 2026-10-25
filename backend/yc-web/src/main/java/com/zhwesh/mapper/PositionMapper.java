package com.zhwesh.mapper;

import com.zhwesh.entity.Position;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface PositionMapper {
    /**
     * 查询账户持仓
     * @param accountId 资金账户id
     * @return 持仓列表
     */
    @Select("SELECT id, account_id, stock_code AS code, stock_name, cost_price, quantity, " +
        "available_quantity, today_buy_quantity FROM `position` " +
        "WHERE account_id = #{accountId} AND quantity > 0")
    List<Position> getPositions(Long accountId);

    /**
     * 查询单个持仓
     * @param accountId 资金账户id
     * @param code 股票代码
     * @return 持仓
     */
    @Select("SELECT id, account_id, stock_code AS code, stock_name, cost_price, quantity, " +
        "available_quantity, today_buy_quantity FROM `position` " +
        "WHERE account_id = #{accountId} AND stock_code = #{code}")
    Position getPosition(@Param("accountId") Long accountId, @Param("code") String code);

    /**
     * 冻结持仓
     * @param accountId 资金账户id
     * @param code 股票代码
     * @param quantity 冻结数量
     * @return 受影响行数
     */
    @Update("UPDATE `position` SET available_quantity = available_quantity - #{quantity} " +
        "WHERE account_id = #{accountId} AND stock_code = #{code} AND available_quantity >= #{quantity}")
    int freezePosition(@Param("accountId") Long accountId, @Param("code") String code,
        @Param("quantity") long quantity);

    /**
     * 解冻持仓
     * @param accountId 资金账户id
     * @param code 股票代码
     * @param quantity 解冻数量
     */
    @Update("UPDATE `position` SET available_quantity = available_quantity + #{quantity} " +
        "WHERE account_id = #{accountId} AND stock_code = #{code}")
    void unfreezePosition(@Param("accountId") Long accountId, @Param("code") String code,
        @Param("quantity") long quantity);

    /**
     * 减少持仓
     * @param accountId 资金账户id
     * @param code 股票代码
     * @param quantity 成交数量
     * @return 受影响行数
     */
    @Update("UPDATE `position` SET quantity = quantity - #{quantity} " +
        "WHERE account_id = #{accountId} AND stock_code = #{code} AND quantity >= #{quantity}")
    int decreasePosition(@Param("accountId") Long accountId, @Param("code") String code,
        @Param("quantity") long quantity);

    /**
     * 增加持仓并更新成本价
     * @param accountId 资金账户id
     * @param code 股票代码
     * @param price 成交价格
     * @param quantity 成交数量
     * @return 受影响行数
     */
    @Update("UPDATE `position` SET " +
        "cost_price = (cost_price * quantity + #{price} * #{quantity}) / (quantity + #{quantity}), " +
        "quantity = quantity + #{quantity}, today_buy_quantity = today_buy_quantity + #{quantity} " +
        "WHERE account_id = #{accountId} AND stock_code = #{code}")
    int increasePosition(@Param("accountId") Long accountId, @Param("code") String code,
        @Param("price") BigDecimal price, @Param("quantity") long quantity);

    /**
     * 创建持仓
     * @param accountId 资金账户id
     * @param code 股票代码
     * @param stockName 股票名称
     * @param costPrice 成本价
     * @param quantity 成交数量
     */
    @Insert("INSERT INTO `position`(account_id, stock_code, stock_name, cost_price, quantity, " +
        "available_quantity, today_buy_quantity) " +
        "VALUES(#{accountId}, #{code}, #{stockName}, #{costPrice}, #{quantity}, 0, #{quantity})")
    void insertPosition(@Param("accountId") Long accountId, @Param("code") String code,
        @Param("stockName") String stockName, @Param("costPrice") BigDecimal costPrice,
        @Param("quantity") long quantity);

    /**
     * 重置每日可卖数量
     * @param today 当前交易日
     */
    @Update("UPDATE `position` p SET available_quantity = p.quantity, today_buy_quantity = 0 " +
        "WHERE NOT EXISTS (" +
        "SELECT 1 FROM sold_orders s WHERE s.account_id = p.account_id " +
        "AND s.stock_code = p.stock_code AND s.quantity > 0 " +
        "AND s.create_time >= #{today}) " +
        "AND NOT EXISTS (" +
        "SELECT 1 FROM orders o WHERE o.account_id = p.account_id " +
        "AND o.stock_code = p.stock_code AND o.quantity < 0 " +
        "AND o.status IN (1, 2))")
    void resetDailyAvailable(@Param("today") LocalDate today);
}
