package com.zhwesh.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 股票信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Stock {
    private String code;            // 股票代码
    private String name;            // 股票名称
    private String board;           // 所属板块
    private LocalDate listDate;     // 上市日期
    private Long totalShares;       // 总股本
    private Long circulateShares;   // 流通股本
    private BigDecimal bookValue;   // 每股净资产
    private BigDecimal eps;         // 每股收益
    private BigDecimal initialPrice;// 上市初始价
}
