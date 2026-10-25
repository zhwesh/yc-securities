<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { OrderVO } from '@/types'
import { fmtPrice } from '@/utils/format'
import { marketSocket } from '@/ws/marketSocket'

/**
 * 成交回报弹窗
 * 数据来源为WebSocket的orderUpdate推送，按soldQuantity增量判定新成交
 * 一秒内到达的成交合并为一条弹窗
 */

/**
 * 回报条目
 */
interface ReportItem {
    uid: number
    code: string
    name: string
    // 买卖方向
    side: 'buy' | 'sell'
    // 成交股数
    shares: number
    // 委托价，市价单为null
    price: number | null
    ts: number
}

// 合并窗口，单位毫秒
const WINDOW_MS = 1000
// 显示时长，单位毫秒
const SHOW_MS = 4500
// 委托有效识别窗口，单位毫秒
const FRESH_MS = 5 * 60 * 1000

const router = useRouter()

// 待显示条目
const items = ref<ReportItem[]>([])
// 各委托上一次的累计成交量
const known = new Map<number, number>()

// 待合并条目
let pending: ReportItem[] = []
// 合并定时器句柄
let flushTimer: number | null = null
// 条目序号
let uid = 0
// 取消委托更新监听函数
let offOrderUpdate: (() => void) | null = null

/**
 * 读取委托创建时间
 * @param o 委托信息
 * @returns 毫秒时间戳
 */
function createdTs(o: OrderVO): number {
    const t = Date.parse((o.createTime ?? '').replace(' ', 'T'))
    return Number.isFinite(t) ? t : 0
}

/**
 * 安排一次合并派发
 */
function scheduleFlush(): void {
    if (flushTimer !== null) return
    flushTimer = window.setTimeout(() => {
        flushTimer = null
        flush()
    }, WINDOW_MS)
}

/**
 * 合并待显示条目并上屏
 */
function flush(): void {
    if (!pending.length) return
    const batch = pending
    pending = []

    const merged = new Map<string, ReportItem>()
    for (const it of batch) {
        const key = `${it.code}-${it.side}-${it.price ?? 'm'}`
        const exist = merged.get(key)
        if (exist) exist.shares += it.shares
        else merged.set(key, { ...it })
    }

    const now = Date.now()
    for (const it of merged.values()) {
        const item: ReportItem = { ...it, uid: uid++, ts: now }
        items.value.push(item)
        window.setTimeout(() => remove(item.uid), SHOW_MS)
    }

    if (items.value.length > 4) items.value = items.value.slice(-4)
}

/**
 * 移除指定条目
 * @param target 条目序号
 */
function remove(target: number): void {
    items.value = items.value.filter((x) => x.uid !== target)
}

/**
 * 处理委托更新推送
 * @param o 委托信息
 */
function onUpdate(o: OrderVO): void {
    if (!o?.id) return

    const prev = known.get(o.id)
    const cur = o.soldQuantity ?? 0
    known.set(o.id, cur)
    if (cur <= 0) return

    const isFresh = Date.now() - createdTs(o) < FRESH_MS
    const delta = prev === undefined ? (isFresh ? cur : 0) : cur - prev
    if (delta <= 0) return

    pending.push({
        uid: 0,
        code: o.stockCode,
        name: o.stockName,
        side: o.quantity > 0 ? 'buy' : 'sell',
        shares: delta,
        price: o.price == null ? null : Number(o.price),
        ts: Date.now()
    })
    scheduleFlush()
}

/**
 * 关闭条目并跳转个股详情页
 * @param item 回报条目
 */
function openStock(item: ReportItem): void {
    remove(item.uid)
    void router.push({ name: 'stock-detail', params: { code: item.code } })
}

onMounted(() => {
    offOrderUpdate = marketSocket.onOrderUpdate(onUpdate)
})

onBeforeUnmount(() => {
    offOrderUpdate?.()
    offOrderUpdate = null
    if (flushTimer !== null) window.clearTimeout(flushTimer)
    flushTimer = null
})
</script>

<template>
    <teleport to="body">
        <transition-group name="report" tag="div" class="report-stack">
            <div
                v-for="item in items"
                :key="item.uid"
                class="report-card"
                :class="item.side === 'buy' ? 'is-buy' : 'is-sell'"
                @click="openStock(item)"
            >
                <div class="head">
                    <span class="badge">{{ item.side === 'buy' ? '买入成交' : '卖出成交' }}</span>
                    <span class="name">{{ item.name }}</span>
                    <span class="code">{{ item.code }}</span>
                    <button class="close" title="关闭" @click.stop="remove(item.uid)">×</button>
                </div>
                <div class="body">
                    <span class="shares">{{ item.shares.toLocaleString('en-US') }}股</span>
                    <span class="price">
                        {{ item.price == null ? '市价' : `@${fmtPrice(item.price)}` }}
                    </span>
                </div>
            </div>
        </transition-group>
    </teleport>
</template>

<style scoped>
.report-stack {
    position: fixed;
    right: 18px;
    bottom: 18px;
    z-index: 3000;
    display: flex;
    flex-direction: column;
    gap: 8px;
    pointer-events: none;
}

.report-card {
    pointer-events: auto;
    width: 268px;
    padding: 9px 12px 10px;
    border-radius: 6px;
    background: var(--bg-panel);
    border: 1px solid var(--border);
    border-left: 3px solid var(--flat);
    box-shadow: var(--shadow-lg);
    cursor: pointer;
    transition: box-shadow 0.15s, transform 0.15s;
}

.report-card:hover {
    transform: translateY(-1px);
    box-shadow: 0 10px 28px rgba(15, 23, 42, 0.16);
}

.report-card.is-buy {
    border-left-color: var(--up);
}

.report-card.is-sell {
    border-left-color: var(--down);
}

.head {
    display: flex;
    align-items: center;
    gap: 6px;
}

.badge {
    padding: 0 5px;
    border-radius: 3px;
    font-size: 12px;
    line-height: 18px;
    color: #fff;
}

.is-buy .badge {
    background: var(--up);
}

.is-sell .badge {
    background: var(--down);
}

.name {
    font-size: 14px;
    font-weight: 600;
    color: var(--text-primary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.code {
    font-family: var(--font-num);
    font-size: 12px;
    color: var(--text-secondary);
}

.close {
    margin-left: auto;
    width: 18px;
    height: 18px;
    border: none;
    border-radius: 3px;
    background: transparent;
    color: var(--text-placeholder);
    font-size: 15px;
    line-height: 1;
    cursor: pointer;
}

.close:hover {
    background: var(--bg-hover);
    color: var(--text-primary);
}

.body {
    display: flex;
    align-items: baseline;
    gap: 8px;
    margin-top: 3px;
}

.shares {
    font-family: var(--font-num);
    font-size: 16px;
    font-weight: 700;
    color: var(--text-primary);
}

.price {
    font-family: var(--font-num);
    font-size: 13px;
    color: var(--text-regular);
}

.report-enter-active,
.report-leave-active {
    transition: opacity 0.18s ease, transform 0.18s ease;
}

.report-enter-from {
    opacity: 0;
    transform: translateX(24px);
}

.report-leave-to {
    opacity: 0;
    transform: translateX(24px);
}
</style>
