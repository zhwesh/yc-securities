package com.zhwesh.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 撤单参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelOrderDTO {
    private Long accountId;  // 资金账号id
    private Long orderId;    // 委托单id
}
