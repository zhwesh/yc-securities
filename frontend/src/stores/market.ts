import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { marketApi } from '@/api/market'
import { watchlistApi } from '@/api/watchlist'
import type { IndexVO, StockQuoteVO, StockVO } from '@/types'

/**
 * 行情中心仓库
 */
export const useMarketStore = defineStore('market', () => {
    // 自选股列表
    const watchlist = ref<StockVO[]>([])
    // 行情快照缓存，键为股票代码
    const quotes = ref<Record<string, StockQuoteVO>>({})
    // 指数列表
    const indexes = ref<IndexVO[]>([])
    // 自选股加载状态
    const watchlistLoading = ref(false)
    // 自选股已加载标记
    const watchlistLoaded = ref(false)
    // 指数已加载标记
    const indexesLoaded = ref(false)
    // 指数加载错误信息
    const indexesError = ref('')

    // 自选股代码列表
    const watchCodes = computed(() => watchlist.value.map((s) => s.code))

    // 自选股与行情组合后的列表
    const watchRows = computed(() =>
        watchlist.value.map((s) => ({
            code: s.code,
            name: quotes.value[s.code]?.name || s.name,
            quote: quotes.value[s.code] ?? null
        }))
    )

    /**
     * 判断股票是否在自选列表中
     * @param code 股票代码
     * @returns 在自选列表中返回true
     */
    function isWatched(code: string): boolean {
        return watchlist.value.some((s) => s.code === code)
    }

    /**
     * 合并单条行情快照
     * @param q 行情快照
     */
    function applyQuote(q: StockQuoteVO): void {
        if (!q?.code) return
        quotes.value[q.code] = { ...(quotes.value[q.code] ?? {}), ...q } as StockQuoteVO
    }

    /**
     * 合并多条行情快照
     * @param list 行情快照列表
     */
    function applyQuotes(list: StockQuoteVO[]): void {
        list.forEach(applyQuote)
    }

    /**
     * 加载自选股列表
     */
    async function loadWatchlist(): Promise<void> {
        watchlistLoading.value = true
        try {
            watchlist.value = await watchlistApi.list()
            watchlistLoaded.value = true
        } catch (e) {
            console.warn('[watchlist] 加载失败', e)
        } finally {
            watchlistLoading.value = false
        }
    }

    /**
     * 加载自选股行情快照
     */
    async function loadWatchQuotes(): Promise<void> {
        const codes = watchCodes.value
        if (!codes.length) return
        try {
            applyQuotes(await marketApi.quotes(codes))
        } catch {
            /* 行情请求失败不影响列表展示 */
        }
    }

    /**
     * 添加自选股并刷新列表与行情
     * @param code 股票代码
     */
    async function addToWatchlist(code: string): Promise<void> {
        await watchlistApi.add(code)
        await loadWatchlist()
        await loadWatchQuotes()
    }

    /**
     * 删除自选股
     * @param code 股票代码
     */
    async function removeFromWatchlist(code: string): Promise<void> {
        await watchlistApi.remove(code)
        watchlist.value = watchlist.value.filter((s) => s.code !== code)
    }

    /**
     * 保存自选股顺序
     * @param codes 按展示顺序排列的股票代码
     */
    async function reorderWatchlist(codes: string[]): Promise<void> {
        await watchlistApi.reorder(codes)
        const map = new Map(watchlist.value.map((s) => [s.code, s]))
        watchlist.value = codes.map((c) => map.get(c)).filter((s): s is StockVO => !!s)
    }

    /**
     * 加载指数行情
     */
    async function loadIndexes(): Promise<void> {
        try {
            indexes.value = await marketApi.indexes()
            indexesLoaded.value = true
            indexesError.value = ''
        } catch (e) {
            indexesError.value = '指数行情加载失败'
            console.warn('[indexes] 加载失败', e)
        }
    }

    /**
     * 重置仓库状态
     */
    function reset(): void {
        watchlist.value = []
        quotes.value = {}
        indexes.value = []
        watchlistLoaded.value = false
        indexesLoaded.value = false
        indexesError.value = ''
    }

    return {
        watchlist,
        watchCodes,
        watchRows,
        quotes,
        indexes,
        watchlistLoading,
        watchlistLoaded,
        indexesLoaded,
        indexesError,
        isWatched,
        applyQuote,
        applyQuotes,
        loadWatchlist,
        loadWatchQuotes,
        addToWatchlist,
        removeFromWatchlist,
        reorderWatchlist,
        loadIndexes,
        reset
    }
})
