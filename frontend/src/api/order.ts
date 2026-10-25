import { http } from './http'
import type { OrderVO, PageResult } from '@/types'

/**
 * 委托查询参数
 */
export interface OrderQuery {
    accountId: number
    code?: string
    // 委托状态，1未成交2部分成交3全部成交4已撤单
    status?: number
    // 起始时间，格式yyyy-MM-dd HH:mm:ss
    startTime?: string
    // 结束时间，格式yyyy-MM-dd HH:mm:ss
    endTime?: string
    pageNum?: number
    pageSize?: number
}

/**
 * 委托接口
 */
export const orderApi = {
    /**
     * 创建委托
     * @param data 委托参数
     * @returns 委托信息
     */
    create(data: {
        accountId: number
        code: string
        // 委托类型，1限价2市价
        type: number
        price: number | null
        // 委托数量，单位股，买入为正卖出为负
        quantity: number
        // 幂等键
        orderKey: string
    }): Promise<OrderVO> {
        return http.post<OrderVO>('/order', data, { silent: true })
    },

    /**
     * 分页查询委托
     * @param params 查询参数
     * @returns 委托分页结果
     */
    list(params: OrderQuery): Promise<PageResult<OrderVO>> {
        return http.get<PageResult<OrderVO>>('/order', params as unknown as Record<string, unknown>)
    },

    /**
     * 查询委托详情
     * @param id 委托标识
     * @returns 委托信息
     */
    detail(id: number): Promise<OrderVO> {
        return http.get<OrderVO>(`/order/${id}`)
    },

    /**
     * 撤销委托
     * @param accountId 资金账户标识
     * @param orderId 委托标识
     * @returns 无返回值
     */
    cancel(accountId: number, orderId: number): Promise<void> {
        return http.put<void>('/order/cancel', { accountId, orderId }, { silent: true })
    }
}
