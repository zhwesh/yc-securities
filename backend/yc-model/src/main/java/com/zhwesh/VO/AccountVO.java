package com.zhwesh.VO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 资金账户信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountVO {
    private Long id;                    // 资金账号id
    private BigDecimal availableAssets; // 可用资产
    private List<String> boards;        // 可交易板块列表
    private Integer status;             // 状态：1正常 2冻结
    private LocalDateTime createTime;   // 开户时间
}
