import { http } from './http'
import type { StockVO } from '@/types'

/**
 * 自选股接口
 */
export const watchlistApi = {
    /**
     * 查询自选股列表
     * @returns 股票列表，按自定义顺序排列
     */
    list(): Promise<StockVO[]> {
        return http.get<StockVO[]>('/watchlist')
    },

    /**
     * 添加自选股
     * @param code 股票代码
     * @returns 无返回值
     */
    add(code: string): Promise<void> {
        return http.post<void>('/watchlist', { code })
    },

    /**
     * 删除自选股
     * @param code 股票代码
     * @returns 无返回值
     */
    remove(code: string): Promise<void> {
        return http.delete<void>('/watchlist', { params: { code } })
    },

    /**
     * 覆盖自选股列表并保存顺序
     * @param codes 股票代码列表
     * @returns 无返回值
     */
    reorder(codes: string[]): Promise<void> {
        return http.put<void>('/watchlist', { codes })
    }
}
