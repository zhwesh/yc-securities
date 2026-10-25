<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useMarketStore } from '@/stores/market'
import type { StockQuoteVO } from '@/types'
import { fmtPercent, fmtPrice, trendClass } from '@/utils/format'
import { marketSocket } from '@/ws/marketSocket'

const router = useRouter()
const market = useMarketStore()

// 代码输入框内容
const input = ref('')
// 添加请求进行中标记
const adding = ref(false)
// 加载状态
const loading = ref(false)

// 取消订阅函数
let offSub: (() => void) | null = null

// 自选股数量是否已达上限
const maxReached = computed(() => market.watchlist.length >= 50)

/**
 * 订阅自选股行情
 */
function subscribe(): void {
    offSub?.()
    offSub = null
    const codes = market.watchCodes
    if (!codes.length) return
    offSub = marketSocket.subscribe(
        codes,
        {
            quote: (q: StockQuoteVO) => market.applyQuote(q)
        },
        { priority: true }
    )
}

/**
 * 刷新自选股列表与行情
 */
async function refresh(): Promise<void> {
    loading.value = true
    try {
        await market.loadWatchlist()
        await market.loadWatchQuotes()
        subscribe()
    } finally {
        loading.value = false
    }
}

/**
 * 添加自选股
 */
async function add(): Promise<void> {
    const code = input.value.trim()
    if (!/^\d{6}$/.test(code)) {
        ElMessage.warning('请输入6位股票代码')
        return
    }
    if (market.isWatched(code)) {
        ElMessage.info('该股票已在自选中')
        return
    }
    if (maxReached.value) {
        ElMessage.warning('自选股最多50只')
        return
    }
    adding.value = true
    try {
        await market.addToWatchlist(code)
        input.value = ''
        subscribe()
        ElMessage.success('已加入自选')
    } catch {
        /* 请求异常由拦截器提示 */
    } finally {
        adding.value = false
    }
}

/**
 * 移除自选股
 * @param code 股票代码
 * @param name 股票名称
 * @param ev 点击事件
 */
async function remove(code: string, name: string, ev: Event): Promise<void> {
    ev.stopPropagation()
    try {
        await market.removeFromWatchlist(code)
        subscribe()
        ElMessage.success(`已从自选中移除${name}`)
    } catch {
        /* 请求异常由拦截器提示 */
    }
}

/**
 * 调整自选股顺序
 * @param code 股票代码
 * @param delta 移动方向，-1上移，1下移
 * @param ev 点击事件
 */
async function move(code: string, delta: number, ev: Event): Promise<void> {
    ev.stopPropagation()
    const codes = [...market.watchCodes]
    const i = codes.indexOf(code)
    const j = i + delta
    if (i < 0 || j < 0 || j >= codes.length) return
    ;[codes[i], codes[j]] = [codes[j], codes[i]]
    try {
        await market.reorderWatchlist(codes)
    } catch {
        /* 请求异常由拦截器提示 */
    }
}

/**
 * 打开个股详情页
 * @param code 股票代码
 */
function open(code: string): void {
    void router.push({ name: 'stock-detail', params: { code } })
}

/**
 * 获取指定代码的行情快照
 * @param code 股票代码
 * @returns 行情快照，无数据时返回null
 */
function rowOf(code: string): StockQuoteVO | null {
    return market.quotes[code] ?? null
}

onMounted(async () => {
    if (!market.watchlistLoaded) {
        await market.loadWatchlist()
    }
    await market.loadWatchQuotes()
    subscribe()
})

onBeforeUnmount(() => {
    offSub?.()
    offSub = null
})
</script>

<template>
    <div class="watch panel">
        <div class="watch-head">
            <span class="title">
                自选股
                <span class="count">{{ market.watchlist.length }}/50</span>
            </span>
            <el-button text size="small" :loading="loading" @click="refresh">
                <el-icon><Refresh /></el-icon>
            </el-button>
        </div>

        <div class="add-row">
            <el-input
                v-model="input"
                size="small"
                placeholder="输入6位代码加入自选"
                maxlength="6"
                :disabled="adding"
                @keyup.enter="add"
            >
                <template #prefix><el-icon><Plus /></el-icon></template>
            </el-input>
            <el-button
                size="small"
                type="primary"
                plain
                :loading="adding"
                :disabled="maxReached"
                @click="add"
            >
                添加
            </el-button>
        </div>

        <div v-if="!market.watchlist.length" class="empty-tip">
            还没有自选股，可在右侧排行榜点击☆添加
        </div>

        <ul v-else class="watch-list">
            <li
                v-for="(item, idx) in market.watchlist"
                :key="item.code"
                class="watch-item"
                @click="open(item.code)"
            >
                <div class="col-name">
                    <div class="name ellipsis">{{ rowOf(item.code)?.name || item.name }}</div>
                    <div class="code num">{{ item.code }}</div>
                </div>

                <div class="col-price num" :class="trendClass(rowOf(item.code)?.changePercent)">
                    {{ fmtPrice(rowOf(item.code)?.price) }}
                </div>

                <div class="col-pct num" :class="trendClass(rowOf(item.code)?.changePercent)">
                    {{ fmtPercent(rowOf(item.code)?.changePercent) }}
                </div>

                <div class="col-actions" @click.stop>
                    <button
                        class="act"
                        title="上移"
                        :disabled="idx === 0"
                        @click="move(item.code, -1, $event)"
                    >
                        <el-icon><Top /></el-icon>
                    </button>
                    <button
                        class="act"
                        title="下移"
                        :disabled="idx === market.watchlist.length - 1"
                        @click="move(item.code, 1, $event)"
                    >
                        <el-icon><Bottom /></el-icon>
                    </button>
                    <button
                        class="act danger"
                        title="删除"
                        @click="remove(item.code, rowOf(item.code)?.name || item.name, $event)"
                    >
                        <el-icon><Close /></el-icon>
                    </button>
                </div>
            </li>
        </ul>
    </div>
</template>

<style scoped>
.watch {
    display: flex;
    flex-direction: column;
    min-height: 0;
    overflow: hidden;
}

.watch-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 42px;
    padding: 0 10px 0 14px;
    border-bottom: 1px solid var(--divider);
}

.title {
    font-size: 15px;
    font-weight: 600;
    color: var(--text-primary);
}

.count {
    margin-left: 6px;
    font-size: 13px;
    font-weight: 400;
    color: var(--text-placeholder);
}

.add-row {
    display: flex;
    gap: 6px;
    padding: 10px 12px;
    border-bottom: 1px solid var(--divider);
}

.watch-list {
    flex: 1 1 auto;
    min-height: 0;
    overflow-y: auto;
}

.watch-item {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 76px 76px;
    align-items: center;
    gap: 6px;
    height: 46px;
    padding: 0 12px;
    border-bottom: 1px solid var(--divider);
    cursor: pointer;
    position: relative;
    transition: background 0.12s;
}

.watch-item:hover {
    background: var(--bg-hover);
}

.col-name {
    min-width: 0;
}

.name {
    font-size: 15px;
    color: var(--text-primary);
    line-height: 1.35;
}

.code {
    font-size: 13px;
    color: var(--text-placeholder);
}

.col-price {
    text-align: right;
    font-size: 15px;
    font-weight: 600;
}

.col-pct {
    text-align: right;
    font-size: 14px;
}

.col-actions {
    position: absolute;
    right: 8px;
    top: 50%;
    transform: translateY(-50%);
    display: none;
    align-items: center;
    gap: 1px;
    padding-left: 10px;
    background: linear-gradient(90deg, transparent, var(--bg-hover) 30%);
}

.watch-item:hover .col-actions {
    display: flex;
}

.act {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 20px;
    height: 20px;
    border: none;
    border-radius: 3px;
    background: transparent;
    color: var(--text-secondary);
    cursor: pointer;
    font-size: 14px;
}

.act:hover:not(:disabled) {
    background: var(--bg-panel);
    color: var(--text-primary);
}

.act.danger:hover {
    color: var(--up);
}

.act:disabled {
    opacity: 0.32;
    cursor: not-allowed;
}
</style>
