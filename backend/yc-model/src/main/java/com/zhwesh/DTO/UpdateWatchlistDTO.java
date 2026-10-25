package com.zhwesh.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 更新自选股排序所需信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateWatchlistDTO {
    private List<String> codes; // 股票代码列表（按展示顺序）
}
