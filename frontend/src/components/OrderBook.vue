<script setup lang="ts">
import { computed } from 'vue'
import type { OrderBookItemVO, OrderBookVO } from '@/types'
import { fmtLots, fmtPrice, trendClass } from '@/utils/format'

const props = defineProps<{
    book: OrderBookVO | null
    preClose: number
    // 现价，省略时使用昨收
    price?: number
    // 档位数
    depth?: number
    dense?: boolean
}>()

/**
 * 盘口行
 */
interface Slot {
    // 档位标签
    label: string
    // 档位数据，无挂单时为null
    item: OrderBookItemVO | null
}

// 档位数量
const depthCount = computed(() => props.depth ?? props.book?.depth ?? 5)

// 最大挂单量，用于计算柱状图宽度
const maxVolume = computed(() => {
    const vols = [...(props.book?.asks ?? []), ...(props.book?.bids ?? [])].map((x) => x.volume)
    return vols.length ? Math.max(...vols) : 0
})

// 卖盘行，缺档时补空行
const askRows = computed<Slot[]>(() => {
    const list = props.book?.asks ?? []
    const n = Math.min(list.length, depthCount.value)
    const rows: Slot[] = []
    for (let i = 0; i < depthCount.value; i += 1) {
        const level = depthCount.value - i
        rows.push({ label: `卖${level}`, item: level <= n ? list[level - 1] : null })
    }
    return rows
})

// 买盘行，缺档时补空行
const bidRows = computed<Slot[]>(() => {
    const list = props.book?.bids ?? []
    const n = Math.min(list.length, depthCount.value)
    const rows: Slot[] = []
    for (let i = 0; i < depthCount.value; i += 1) {
        const level = i + 1
        rows.push({ label: `买${level}`, item: level <= n ? list[level - 1] : null })
    }
    return rows
})

/**
 * 计算柱状图宽度百分比
 * @param v 挂单量
 * @returns 百分比字符串
 */
function barWidth(v: number | undefined): string {
    if (!v || !maxVolume.value) return '0%'
    return `${Math.min(100, (v / maxVolume.value) * 100)}%`
}

/**
 * 获取价格样式类名
 * @param item 档位数据
 * @returns up、down或flat
 */
function priceClass(item: OrderBookItemVO | null): string {
    if (!item) return 'flat'
    return trendClass(item.price - props.preClose)
}

// 现价
const currentPrice = computed(() => props.price || props.preClose)
// 现价样式类名
const currentCls = computed(() => trendClass(currentPrice.value - props.preClose))
</script>

<template>
    <div class="order-book">
        <div class="ob-head">
            <span class="col-label">档位</span>
            <span class="col-price">价格</span>
            <span class="col-vol">数量(手)</span>
        </div>

        <div class="ob-body" :class="{ dense }">
            <div
                v-for="row in askRows"
                :key="row.label"
                class="ob-row ask"
                :class="{ 'is-empty': !row.item }"
            >
                <span class="col-label">{{ row.label }}</span>
                <span class="col-price num" :class="priceClass(row.item)">
                    {{ row.item ? fmtPrice(row.item.price) : '--' }}
                </span>
                <span class="col-vol num">{{ row.item ? fmtLots(row.item.volume) : '--' }}</span>
                <i v-if="row.item" class="bar ask-bar" :style="{ width: barWidth(row.item.volume) }" />
            </div>

            <div class="ob-sep">
                <span class="sep-label">现价</span>
                <span class="sep-price num" :class="currentCls">{{ fmtPrice(currentPrice) }}</span>
            </div>

            <div
                v-for="row in bidRows"
                :key="row.label"
                class="ob-row bid"
                :class="{ 'is-empty': !row.item }"
            >
                <span class="col-label">{{ row.label }}</span>
                <span class="col-price num" :class="priceClass(row.item)">
                    {{ row.item ? fmtPrice(row.item.price) : '--' }}
                </span>
                <span class="col-vol num">{{ row.item ? fmtLots(row.item.volume) : '--' }}</span>
                <i v-if="row.item" class="bar bid-bar" :style="{ width: barWidth(row.item.volume) }" />
            </div>
        </div>
    </div>
</template>

<style scoped>
.order-book {
    display: flex;
    flex-direction: column;
    font-size: 14px;
}

.ob-head {
    display: grid;
    grid-template-columns: 44px 1fr 1fr;
    align-items: center;
    height: 26px;
    padding: 0 10px;
    font-size: 13px;
    color: var(--text-secondary);
    border-bottom: 1px solid var(--divider);
}

.ob-head .col-price,
.ob-head .col-vol {
    text-align: right;
}

.ob-body {
    display: flex;
    flex-direction: column;
}

.ob-row {
    position: relative;
    display: grid;
    grid-template-columns: 44px 1fr 1fr;
    align-items: center;
    height: 22px;
    padding: 0 10px;
    overflow: hidden;
}

.ob-body.dense .ob-row {
    height: 20px;
}

.ob-row.is-empty .col-price,
.ob-row.is-empty .col-vol {
    color: var(--text-placeholder);
}

.col-label {
    color: var(--text-secondary);
    font-size: 13px;
    z-index: 1;
}

.col-price,
.col-vol {
    text-align: right;
    z-index: 1;
}

.col-vol {
    color: var(--text-regular);
}

.bar {
    position: absolute;
    right: 10px;
    top: 3px;
    bottom: 3px;
    border-radius: 2px;
    opacity: 0.5;
    pointer-events: none;
}

.ask-bar {
    background: rgba(229, 72, 77, 0.14);
}

.bid-bar {
    background: rgba(0, 168, 112, 0.14);
}

.ob-sep {
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 24px;
    padding: 0 10px;
    margin: 2px 0;
    background: var(--bg-subtle);
    border-top: 1px solid var(--divider);
    border-bottom: 1px solid var(--divider);
}

.sep-label {
    font-size: 13px;
    color: var(--text-secondary);
}

.sep-price {
    font-weight: 600;
    font-size: 15px;
}
</style>
