package com.zhwesh.VO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 股票列表项信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockVO {
    private String code;        // 股票代码
    private String name;        // 股票名称
}
