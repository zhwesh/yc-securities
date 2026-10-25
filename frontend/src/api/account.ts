import { http } from './http'
import type { AccountVO, BankCardVO, PageResult, PositionVO, TradeVO } from '@/types'

/**
 * 资金账户接口
 */
export const accountApi = {
    /**
     * 查询当前用户的资金账户列表
     * @returns 资金账户列表
     */
    list(): Promise<AccountVO[]> {
        return http.get<AccountVO[]>('/account')
    },

    /**
     * 开通资金账户
     * @param password 交易密码
     * @returns 无返回值
     */
    open(password: string): Promise<void> {
        return http.post<void>('/account/open', { password })
    },

    /**
     * 开通板块交易权限
     * @param accountId 资金账户标识
     * @param boards 板块名称列表
     * @returns 无返回值
     */
    openBoard(accountId: number, boards: string[]): Promise<void> {
        return http.put<void>('/account/board/open', { accountId, boards })
    },

    /**
     * 修改交易密码
     * @param accountId 资金账户标识
     * @param oldPassword 原交易密码
     * @param newPassword 新交易密码
     * @returns 无返回值
     */
    updateTradePassword(accountId: number, oldPassword: string, newPassword: string): Promise<void> {
        return http.put<void>('/account/password/update', { accountId, oldPassword, newPassword })
    },

    /**
     * 验证交易密码并设置在线时长
     * @param accountId 资金账户标识
     * @param tradePassword 交易密码
     * @param onlineHours 保持在线时长
     * @param silent 为true时抑制默认错误提示
     * @returns 无返回值
     */
    verifyTradePassword(
        accountId: number,
        tradePassword: string,
        onlineHours: 6 | 12 | 24,
        silent = false
    ): Promise<void> {
        return http.post<void>(
            '/account/password/verify',
            { accountId, tradePassword, onlineHours },
            { silent }
        )
    },

    /**
     * 入金
     * @param accountId 资金账户标识
     * @param amount 入金金额
     * @returns 无返回值
     */
    deposit(accountId: number, amount: number): Promise<void> {
        return http.put<void>('/account/deposit', { accountId, amount })
    },

    /**
     * 出金
     * @param accountId 资金账户标识
     * @param amount 出金金额
     * @param silent 为true时抑制默认错误提示
     * @returns 无返回值
     */
    withdraw(accountId: number, amount: number, silent = false): Promise<void> {
        return http.put<void>('/account/withdraw', { accountId, amount }, { silent })
    }
}

/************************************************** */

/**
 * 银行卡接口
 */
export const bankCardApi = {
    /**
     * 查询指定资金账户的银行卡
     * @param accountId 资金账户标识
     * @returns 银行卡列表
     */
    list(accountId: number): Promise<BankCardVO[]> {
        return http.get<BankCardVO[]>('/bank-card', { accountId })
    },

    /**
     * 绑定银行卡
     * @param data 绑卡参数
     * @returns 无返回值
     */
    bind(data: {
        accountId: number
        cardNumber: string
        bankName: string
        phone: string
        code: string
    }): Promise<void> {
        return http.post<void>('/bank-card', data)
    },

    /**
     * 解绑银行卡，参数置于请求体
     * @param data 解绑参数
     * @returns 无返回值
     */
    unbind(data: {
        accountId: number
        cardNumber: string
        phone: string
        code: string
    }): Promise<void> {
        return http.delete<void>('/bank-card', { data })
    },

    /**
     * 发送银行卡验证码
     * @param phone 银行预留手机号
     * @returns 无返回值
     */
    sendCode(phone: string): Promise<void> {
        return http.get<void>('/bank-card/code', { phone })
    },

    /**
     * 校验卡号与预留手机号是否匹配
     * @param cardNumber 银行卡号
     * @param phone 银行预留手机号
     * @returns 无返回值
     */
    verify(cardNumber: string, phone: string): Promise<void> {
        return http.post<void>('/bank-card/verify', { cardNumber, phone })
    }
}

/************************************************** */

/**
 * 持仓接口
 */
export const positionApi = {
    /**
     * 查询指定资金账户的持仓
     * @param accountId 资金账户标识
     * @param silent 为true时抑制默认错误提示
     * @returns 持仓列表
     */
    list(accountId: number, silent = false): Promise<PositionVO[]> {
        return http.get<PositionVO[]>('/position', { accountId }, { silent })
    }
}

/************************************************** */

/**
 * 成交记录查询参数
 */
export interface TradeQuery {
    accountId: number
    code?: string
    // 起始时间，格式yyyy-MM-dd HH:mm:ss
    startTime?: string
    // 结束时间，格式yyyy-MM-dd HH:mm:ss
    endTime?: string
    pageNum?: number
    pageSize?: number
}

/**
 * 成交记录接口
 */
export const tradeApi = {
    /**
     * 分页查询成交记录
     * @param params 查询参数
     * @returns 成交记录分页结果
     */
    list(params: TradeQuery): Promise<PageResult<TradeVO>> {
        return http.get<PageResult<TradeVO>>('/trade', params as unknown as Record<string, unknown>)
    }
}
