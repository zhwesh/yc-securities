import type { OrderBookVO, OrderVO, StockQuoteVO, StockTradeDetailVO } from '@/types'
import { getToken } from '@/utils/authToken'

/**
 * 行情WebSocket单例
 * 连接地址：ws://host/ws?token=令牌
 * 发送消息：subscribe、unsubscribe、ping
 * 推送消息：quote、orderBook、trade、orderUpdate、pong
 * 单连接订阅上限50只
 */

/**
 * 连接状态
 */
export type WsStatus = 'idle' | 'connecting' | 'open' | 'closed'

/**
 * 取消订阅函数
 */
type Unsubscribe = () => void

/**
 * 事件处理器集合
 */
interface Handlers {
    quote: Set<(q: StockQuoteVO) => void>
    orderBook: Set<(code: string, book: OrderBookVO) => void>
    trade: Set<(code: string, t: StockTradeDetailVO) => void>
    orderUpdate: Set<(o: OrderVO) => void>
    status: Set<(s: WsStatus) => void>
}

// 单连接订阅上限
const MAX_SUBSCRIPTIONS = 50
// 心跳间隔，单位毫秒
const PING_INTERVAL = 20000
// 重连最大退避时长，单位毫秒
const MAX_BACKOFF = 8000
// 行情合并窗口，单位毫秒
const QUOTE_FLUSH_MS = 200

/**
 * 行情连接管理
 */
class MarketSocket {
    // 连接实例
    private ws: WebSocket | null = null
    // 当前令牌
    private token = ''
    // 连接状态
    private status: WsStatus = 'idle'
    // 订阅引用计数，键为股票代码
    private counts = new Map<string, number>()
    // 已发送至服务端的股票代码
    private sent = new Set<string>()
    // 事件处理器集合
    private handlers: Handlers = {
        quote: new Set(),
        orderBook: new Set(),
        trade: new Set(),
        orderUpdate: new Set(),
        status: new Set()
    }
    // 心跳定时器句柄
    private pingTimer: number | null = null
    // 重连定时器句柄
    private reconnectTimer: number | null = null
    // 订阅同步定时器句柄
    private flushTimer: number | null = null
    // 行情合并缓冲，键为股票代码
    private quoteBuffer = new Map<string, StockQuoteVO>()
    // 行情派发定时器句柄
    private quoteTimer: number | null = null
    // 高优先级订阅引用计数
    private priorityCounts = new Map<string, number>()
    // 重连次数
    private retry = 0
    // 主动断开标记
    private manualClose = false

    /************************************************** */

    /**
     * 建立连接
     * @param token 访问令牌，省略时读取本地存储
     */
    connect(token?: string): void {
        const curToken = token ?? getToken()
        if (!curToken) return
        if (this.ws && this.token === curToken && this.status !== 'closed') return

        this.dispose(true)
        this.token = curToken
        this.manualClose = false
        this.open()
    }

    /**
     * 断开连接并清除订阅
     */
    disconnect(): void {
        this.manualClose = true
        this.dispose(true)
        this.setStatus('idle')
    }

    /**
     * 创建连接实例
     */
    private open(): void {
        if (!this.token) return
        this.setStatus('connecting')

        const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
        const url = `${proto}//${window.location.host}/ws?token=${encodeURIComponent(this.token)}`

        let ws: WebSocket
        try {
            ws = new WebSocket(url)
        } catch {
            this.scheduleReconnect()
            return
        }
        this.ws = ws
        this.sent.clear()

        ws.onopen = () => {
            this.retry = 0
            this.setStatus('open')
            this.startPing()
            this.flush()
        }

        ws.onmessage = (ev) => {
            this.dispatch(ev.data)
        }

        ws.onerror = () => {
            /* 关闭回调随后触发，此处不作处理 */
        }

        ws.onclose = () => {
            this.stopPing()
            this.sent.clear()
            if (this.manualClose) {
                this.setStatus('idle')
                return
            }
            this.setStatus('closed')
            this.scheduleReconnect()
        }
    }

    /**
     * 安排重连，间隔按指数退避递增
     */
    private scheduleReconnect(): void {
        if (this.manualClose || this.reconnectTimer !== null) return
        const delay = Math.min(MAX_BACKOFF, 500 * 2 ** this.retry)
        this.retry += 1
        this.reconnectTimer = window.setTimeout(() => {
            this.reconnectTimer = null
            if (!this.manualClose && this.token) this.open()
        }, delay)
    }

    /**
     * 释放连接与定时器
     * @param clearSubs 为true时同时清除订阅计数
     */
    private dispose(clearSubs: boolean): void {
        this.stopPing()
        if (this.quoteTimer !== null) {
            window.clearTimeout(this.quoteTimer)
            this.quoteTimer = null
        }
        this.quoteBuffer.clear()
        if (this.reconnectTimer !== null) {
            window.clearTimeout(this.reconnectTimer)
            this.reconnectTimer = null
        }
        if (this.flushTimer !== null) {
            window.clearTimeout(this.flushTimer)
            this.flushTimer = null
        }
        if (this.ws) {
            const ws = this.ws
            this.ws = null
            ws.onopen = ws.onmessage = ws.onerror = ws.onclose = null
            try {
                ws.close()
            } catch {
                /* 忽略关闭异常 */
            }
        }
        this.sent.clear()
        if (clearSubs) this.counts.clear()
    }

    /**
     * 启动心跳定时器
     */
    private startPing(): void {
        this.stopPing()
        this.pingTimer = window.setInterval(() => {
            this.send({ type: 'ping' })
        }, PING_INTERVAL)
    }

    /**
     * 停止心跳定时器
     */
    private stopPing(): void {
        if (this.pingTimer !== null) {
            window.clearInterval(this.pingTimer)
            this.pingTimer = null
        }
    }

    /**
     * 更新连接状态并通知监听者
     * @param s 连接状态
     */
    private setStatus(s: WsStatus): void {
        if (this.status === s) return
        this.status = s
        this.handlers.status.forEach((fn) => fn(s))
    }

    /**
     * 获取当前连接状态
     * @returns 连接状态
     */
    getStatus(): WsStatus {
        return this.status
    }

    /**
     * 发送消息
     * @param payload 消息内容
     */
    private send(payload: unknown): void {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            try {
                this.ws.send(JSON.stringify(payload))
            } catch {
                /* 忽略发送异常 */
            }
        }
    }

    /************************************************** */

    /**
     * 缓存行情快照并按窗口合并派发
     * @param q 行情快照
     */
    private queueQuote(q: StockQuoteVO): void {
        this.quoteBuffer.set(q.code, q)
        if (this.quoteTimer !== null) return
        this.quoteTimer = window.setTimeout(() => {
            this.quoteTimer = null
            const batch = [...this.quoteBuffer.values()]
            this.quoteBuffer.clear()
            for (const quote of batch) {
                this.handlers.quote.forEach((fn) => fn(quote))
            }
        }, QUOTE_FLUSH_MS)
    }

    /**
     * 解析推送消息并分发至对应处理器
     * @param raw 原始消息
     */
    private dispatch(raw: unknown): void {
        if (typeof raw !== 'string') return
        let msg: { type?: string; code?: string; data?: unknown }
        try {
            msg = JSON.parse(raw)
        } catch {
            return
        }
        if (!msg || typeof msg.type !== 'string') return

        switch (msg.type) {
            case 'quote': {
                const q = msg.data as StockQuoteVO | undefined
                if (q?.code) this.queueQuote(q)
                break
            }
            case 'orderBook': {
                const book = msg.data as OrderBookVO | undefined
                const code = msg.code ?? book?.code ?? ''
                if (code && book) this.handlers.orderBook.forEach((fn) => fn(code, book))
                break
            }
            case 'trade': {
                const t = msg.data as StockTradeDetailVO | undefined
                if (t && msg.code) this.handlers.trade.forEach((fn) => fn(msg.code as string, t))
                break
            }
            case 'orderUpdate': {
                const o = msg.data as OrderVO | undefined
                if (o?.id) this.handlers.orderUpdate.forEach((fn) => fn(o))
                break
            }
            default:
                break
        }
    }

    /************************************************** */

    /**
     * 订阅股票并登记事件处理器
     * @param codes 股票代码列表
     * @param handlers 事件处理器
     * @param opts 订阅选项，priority为true时优先占用订阅配额
     * @returns 取消订阅函数
     */
    subscribe(
        codes: string[],
        handlers?: {
            quote?: (q: StockQuoteVO) => void
            orderBook?: (code: string, book: OrderBookVO) => void
            trade?: (code: string, t: StockTradeDetailVO) => void
        },
        opts?: { priority?: boolean }
    ): Unsubscribe {
        const list = [...new Set(codes.filter(Boolean))]
        const priority = opts?.priority === true

        if (list.length > MAX_SUBSCRIPTIONS) {
            console.warn(`[ws] 单次最多订阅${MAX_SUBSCRIPTIONS}只，超出部分已忽略`)
        }

        if (handlers?.quote) this.handlers.quote.add(handlers.quote)
        if (handlers?.orderBook) this.handlers.orderBook.add(handlers.orderBook)
        if (handlers?.trade) this.handlers.trade.add(handlers.trade)

        list.forEach((code) => this.counts.set(code, (this.counts.get(code) ?? 0) + 1))
        if (priority) {
            list.forEach((code) =>
                this.priorityCounts.set(code, (this.priorityCounts.get(code) ?? 0) + 1)
            )
        }
        this.flush()

        let done = false
        return () => {
            if (done) return
            done = true
            if (handlers?.quote) this.handlers.quote.delete(handlers.quote)
            if (handlers?.orderBook) this.handlers.orderBook.delete(handlers.orderBook)
            if (handlers?.trade) this.handlers.trade.delete(handlers.trade)

            if (priority) {
                list.forEach((code) => {
                    const n = (this.priorityCounts.get(code) ?? 1) - 1
                    if (n <= 0) this.priorityCounts.delete(code)
                    else this.priorityCounts.set(code, n)
                })
            }

            const release: string[] = []
            list.forEach((code) => {
                const n = (this.counts.get(code) ?? 1) - 1
                if (n <= 0) {
                    this.counts.delete(code)
                    release.push(code)
                } else {
                    this.counts.set(code, n)
                }
            })
            if (release.length) this.unsubscribeCodes(release)
        }
    }

    /**
     * 向服务端发送取消订阅
     * @param codes 股票代码列表
     */
    private unsubscribeCodes(codes: string[]): void {
        const toSend = codes.filter((c) => this.sent.has(c))
        if (!toSend.length) return
        toSend.forEach((c) => this.sent.delete(c))
        this.send({ type: 'unsubscribe', codes: toSend })
    }

    /**
     * 同步订阅状态差集至服务端
     */
    private flush(): void {
        if (this.flushTimer !== null) return
        this.flushTimer = window.setTimeout(() => {
            this.flushTimer = null
            if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return

            const desired = new Set<string>()
            for (const [code, n] of this.priorityCounts) {
                if (n > 0 && desired.size < MAX_SUBSCRIPTIONS) desired.add(code)
            }
            for (const [code, n] of this.counts) {
                if (n > 0 && desired.size < MAX_SUBSCRIPTIONS) desired.add(code)
            }

            const toAdd = [...desired].filter((c) => !this.sent.has(c))
            const toRemove = [...this.sent].filter((c) => !desired.has(c))

            if (toRemove.length) {
                toRemove.forEach((c) => this.sent.delete(c))
                this.send({ type: 'unsubscribe', codes: toRemove })
            }
            if (toAdd.length) {
                toAdd.forEach((c) => this.sent.add(c))
                this.send({ type: 'subscribe', codes: toAdd })
            }
        }, 0)
    }

    /************************************************** */

    /**
     * 注册行情处理器
     * @param fn 处理器
     * @returns 取消注册函数
     */
    onQuote(fn: (q: StockQuoteVO) => void): Unsubscribe {
        this.handlers.quote.add(fn)
        return () => this.handlers.quote.delete(fn)
    }

    /**
     * 注册盘口处理器
     * @param fn 处理器
     * @returns 取消注册函数
     */
    onOrderBook(fn: (code: string, book: OrderBookVO) => void): Unsubscribe {
        this.handlers.orderBook.add(fn)
        return () => this.handlers.orderBook.delete(fn)
    }

    /**
     * 注册成交处理器
     * @param fn 处理器
     * @returns 取消注册函数
     */
    onTrade(fn: (code: string, t: StockTradeDetailVO) => void): Unsubscribe {
        this.handlers.trade.add(fn)
        return () => this.handlers.trade.delete(fn)
    }

    /**
     * 注册委托更新处理器
     * @param fn 处理器
     * @returns 取消注册函数
     */
    onOrderUpdate(fn: (o: OrderVO) => void): Unsubscribe {
        this.handlers.orderUpdate.add(fn)
        return () => this.handlers.orderUpdate.delete(fn)
    }

    /**
     * 注册连接状态处理器，注册时立即回调一次当前状态
     * @param fn 处理器
     * @returns 取消注册函数
     */
    onStatus(fn: (s: WsStatus) => void): Unsubscribe {
        this.handlers.status.add(fn)
        fn(this.status)
        return () => this.handlers.status.delete(fn)
    }
}

export const marketSocket = new MarketSocket()
