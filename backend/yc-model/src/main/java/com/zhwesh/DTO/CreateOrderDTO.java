package com.zhwesh.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 下单参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderDTO {
    private Long accountId;      // 资金账号id
    private String code;         // 股票代码
    private Integer type;        // 委托类型：1限价 2市价
    private BigDecimal price;    // 委托价格（限价单必填，市价单忽略）
    private Integer quantity;    // 委托数量（正数买入负数卖出，须为100的整数倍）
    private String orderKey;     // UUID幂等键
}
