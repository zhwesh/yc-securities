package com.zhwesh.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 添加自选股所需信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddWatchlistDTO {
    private String code;        // 股票代码
}
