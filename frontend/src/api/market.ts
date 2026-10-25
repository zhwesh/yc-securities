import { http } from './http'
import type {
    CallAuctionVO,
    IndexVO,
    KLineVO,
    MinuteKLineVO,
    MinuteVO,
    OrderBookVO,
    PageResult,
    StockDetailVO,
    StockQuoteVO,
    StockTradeDetailVO,
    StockVO
} from '@/types'

/**
 * K线查询参数
 */
export interface KLineQuery {
    code: string
    // 周期，1日线2周线3月线4季线5年线
    period?: number
    // 复权方式，0不复权1前复权2后复权
    adjust?: number
    limit?: number
    // 翻页游标，不含该日期
    endDate?: string
}

/**
 * 行情与股票接口
 */
export const marketApi = {
    /**
     * 查询指数行情
     * @returns 指数列表
     */
    indexes(): Promise<IndexVO[]> {
        return http.get<IndexVO[]>('/market/indexes')
    },

    /**
     * 查询单只股票行情
     * @param code 股票代码
     * @returns 行情快照
     */
    quote(code: string): Promise<StockQuoteVO> {
        return http.get<StockQuoteVO>('/market/quote', { code })
    },

    /**
     * 批量查询行情，最多50只
     * @param codes 股票代码列表
     * @returns 行情快照列表
     */
    quotes(codes: string[]): Promise<StockQuoteVO[]> {
        const list = codes.slice(0, 50)
        if (list.length === 0) return Promise.resolve([])
        return http.get<StockQuoteVO[]>('/market/quotes', { codes: list.join(',') })
    },

    /**
     * 查询五档盘口
     * @param code 股票代码
     * @param depth 档位数
     * @returns 盘口快照
     */
    orderBook(code: string, depth = 5): Promise<OrderBookVO> {
        return http.get<OrderBookVO>('/market/order-book', { code, depth })
    },

    /**
     * 查询排行榜
     * @param params 排序与分页参数
     * @returns 行情快照分页结果
     */
    rank(params: {
        // 排序指标，1价格2涨跌幅3成交量4成交额5换手率6市净率7总市值8流通市值9每股净资产
        sortBy?: number
        // 排序方向，1升序2降序
        order?: number
        pageNum?: number
        pageSize?: number
    }): Promise<PageResult<StockQuoteVO>> {
        return http.get<PageResult<StockQuoteVO>>('/market/rank', params)
    },

    /**
     * 查询分时数据
     * @param code 股票代码
     * @param date 交易日期，省略时取当日
     * @returns 分时数据
     */
    minute(code: string, date?: string): Promise<MinuteVO> {
        return http.get<MinuteVO>('/market/minute', date ? { code, date } : { code })
    },

    /**
     * 查询逐笔成交
     * @param code 股票代码
     * @param limit 返回笔数
     * @returns 成交明细列表
     */
    trades(code: string, limit = 50): Promise<StockTradeDetailVO[]> {
        return http.get<StockTradeDetailVO[]>('/market/trades', { code, limit })
    },

    /**
     * 查询日线及以上周期K线
     * @param query 查询参数
     * @returns K线数据
     */
    kline(query: KLineQuery): Promise<KLineVO> {
        return http.get<KLineVO>('/market/kline', query as unknown as Record<string, unknown>)
    },

    /**
     * 查询分钟级K线
     * @param params 周期、条数与翻页游标
     * @returns 分钟K线数据
     */
    minuteKline(params: {
        code: string
        // 周期，取值1、5、15、30、60
        period?: number
        limit?: number
        // 翻页游标，不含该时间，格式yyyy-MM-dd HH:mm:ss
        endTime?: string
    }): Promise<MinuteKLineVO> {
        return http.get<MinuteKLineVO>(
            '/market/minute-kline',
            params as unknown as Record<string, unknown>
        )
    },

    /**
     * 查询集合竞价快照
     * @param code 股票代码
     * @returns 竞价快照
     */
    auction(code: string): Promise<CallAuctionVO> {
        return http.get<CallAuctionVO>('/market/auction', { code })
    }
}

/************************************************** */

/**
 * 股票信息接口
 */
export const stockApi = {
    /**
     * 按代码或名称前缀分页查询股票
     * @param keyword 关键字
     * @param pageNum 页码
     * @param pageSize 每页条数
     * @returns 股票分页结果
     */
    list(keyword?: string, pageNum = 1, pageSize = 20): Promise<PageResult<StockVO>> {
        return http.get<PageResult<StockVO>>('/stock', {
            keyword: keyword || undefined,
            pageNum,
            pageSize
        })
    },

    /**
     * 查询股票详情
     * @param code 股票代码
     * @returns 股票详情
     */
    detail(code: string): Promise<StockDetailVO> {
        return http.get<StockDetailVO>(`/stock/${code}`)
    }
}
