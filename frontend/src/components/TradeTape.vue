<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { marketApi } from '@/api/market'
import type { StockTradeDetailVO } from '@/types'
import { fmtPrice } from '@/utils/format'
import { marketSocket } from '@/ws/marketSocket'

const props = defineProps<{
    code: string
    preClose: number
    limit?: number
    // 合并窗口，单位毫秒
    refreshMs?: number
}>()

// 成交明细列表
const list = ref<StockTradeDetailVO[]>([])
// 加载状态
const loading = ref(false)
// 错误信息
const error = ref('')

// 取消订阅函数
let offSub: (() => void) | null = null
// 待插入的成交
let pending: StockTradeDetailVO[] = []
// 派发定时器句柄
let flushTimer: number | null = null

/**
 * 获取列表容量
 * @returns 条数上限
 */
const maxLimit = () => props.limit ?? 50

/**
 * 获取合并窗口时长
 * @returns 毫秒数
 */
const flushInterval = () => props.refreshMs ?? 1000

/**
 * 加载成交明细
 */
async function load(): Promise<void> {
    if (!props.code) return
    loading.value = true
    error.value = ''
    try {
        list.value = await marketApi.trades(props.code, maxLimit())
    } catch (e) {
        error.value = e instanceof Error ? e.message : '逐笔成交加载失败'
        list.value = []
    } finally {
        loading.value = false
    }
}

/**
 * 将缓存的成交插入列表头部
 */
function flush(): void {
    flushTimer = null
    if (!pending.length) return
    const batch = pending
    pending = []
    const newestFirst = batch.reverse()
    list.value = [...newestFirst, ...list.value].slice(0, maxLimit())
}

/**
 * 安排一次派发
 */
function scheduleFlush(): void {
    if (flushTimer !== null) return
    flushTimer = window.setTimeout(flush, flushInterval())
}

/**
 * 清除派发定时器与缓存
 */
function clearFlushTimer(): void {
    if (flushTimer !== null) {
        window.clearTimeout(flushTimer)
        flushTimer = null
    }
    pending = []
}

/**
 * 订阅当前股票的成交推送
 */
function subscribe(): void {
    offSub?.()
    offSub = null
    clearFlushTimer()
    offSub = marketSocket.subscribe([props.code], {
        trade: (code, t) => {
            if (code !== props.code) return
            pending.push(t)
            scheduleFlush()
        }
    })
}

/**
 * 将股数换算为手数
 * @param v 股数
 * @returns 手数字符串
 */
function sharesToLots(v: number): string {
    return (v / 100).toFixed(0)
}

/**
 * 获取价格样式类名
 * @param price 成交价
 * @returns up、down或flat
 */
function priceCls(price: number): string {
    if (price > props.preClose) return 'up'
    if (price < props.preClose) return 'down'
    return 'flat'
}

/**
 * 获取主动方向样式类名
 * @param t 成交明细
 * @returns up、down或flat
 */
function sideCls(t: StockTradeDetailVO): string {
    if (t.direction === 0) return 'up'
    if (t.direction === 1) return 'down'
    return 'flat'
}

/**
 * 获取主动方向文本
 * @param t 成交明细
 * @returns 买、卖或--
 */
function sideText(t: StockTradeDetailVO): string {
    if (t.direction === 0) return '买'
    if (t.direction === 1) return '卖'
    return '--'
}

onMounted(async () => {
    await load()
    subscribe()
})

onBeforeUnmount(() => {
    offSub?.()
    offSub = null
    clearFlushTimer()
})

watch(
    () => props.code,
    async () => {
        clearFlushTimer()
        await load()
        subscribe()
    }
)
</script>

<template>
    <div class="tape">
        <div class="tape-head">
            <span class="col-time">时间</span>
            <span class="col-price">价格</span>
            <span class="col-vol">量(手)</span>
            <span class="col-dir">方向</span>
        </div>

        <div v-loading="loading" class="tape-body">
            <div v-for="(t, i) in list" :key="`${t.time}-${i}`" class="tape-row">
                <span class="col-time num">{{ t.time }}</span>
                <span class="col-price num" :class="priceCls(t.price)">{{ fmtPrice(t.price) }}</span>
                <span class="col-vol num" :class="sideCls(t)">{{ sharesToLots(t.volume) }}</span>
                <span class="col-dir" :class="sideCls(t)">{{ sideText(t) }}</span>
            </div>

            <div v-if="!list.length && !loading" class="empty-tip">
                {{ error || '暂无成交' }}
            </div>
        </div>
    </div>
</template>

<style scoped>
.tape {
    display: flex;
    flex-direction: column;
    min-height: 0;
    font-size: 14px;
}

/* 固定列宽之和为156px，剩余宽度分配给价格列 */
.tape-head,
.tape-row {
    display: grid;
    grid-template-columns: 58px minmax(46px, 1fr) 52px 34px;
    align-items: center;
    padding: 0 8px;
    column-gap: 4px;
}

.tape-head {
    height: 26px;
    font-size: 13px;
    color: var(--text-secondary);
    border-bottom: 1px solid var(--divider);
}

.tape-head span {
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.tape-body {
    flex: 1 1 auto;
    min-height: 0;
    overflow-y: auto;
}

.tape-row {
    height: 24px;
}

.tape-row:nth-child(odd) {
    background: var(--bg-subtle);
}

.col-time {
    color: var(--text-secondary);
    font-size: 13px;
}

.col-price,
.col-vol,
.col-dir {
    text-align: right;
    white-space: nowrap;
}

.col-dir {
    font-size: 13px;
}
</style>
