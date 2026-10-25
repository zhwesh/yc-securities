package com.zhwesh.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户自选股
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Watchlist {
    private Long id;                    // 主键
    private Long userId;                // 平台用户id
    private String code;                // 股票代码
    private Integer sortNo;             // 排序号（升序）
    private LocalDateTime createTime;   // 创建时间
}
