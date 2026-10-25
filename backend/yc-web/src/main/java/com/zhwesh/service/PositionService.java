package com.zhwesh.service;

import com.zhwesh.VO.PositionVO;

import java.util.List;

public interface PositionService {
    /**
     * 查询用户持仓
     * @param accountId 资金账户id
     * @return 持仓列表List<PositionVO>
     */
    List<PositionVO> getPositions(Long accountId);
}
