<script setup lang="ts">
import { computed } from 'vue'
import type { StockDetailVO, StockQuoteVO } from '@/types'
import { fmtAmount, fmtChange, fmtLots, fmtPercent, fmtPrice, trendClass } from '@/utils/format'

const props = defineProps<{
    quote: StockQuoteVO | null
    detail: StockDetailVO | null
}>()

const emit = defineEmits<{
    (e: 'action', kind: 'buy' | 'sell' | 'cancel' | 'detail'): void
}>()

/**
 * 计算价格与昨收的差值
 * @param v 价格
 * @returns 差值，无昨收时返回null
 */
function rel(v: number | null | undefined): number | null {
    if (v == null || !props.quote?.preClose) return null
    return v - props.quote.preClose
}

// 指标项列表
const metrics = computed(() => {
    const q = props.quote
    return [
        { label: '开盘价', value: fmtPrice(q?.open) },
        { label: '市值', value: fmtAmount(q?.marketValue) },
        { label: '量比', value: q?.volumeRatio == null ? '--' : q.volumeRatio.toFixed(2) },
        { label: '最高价', value: fmtPrice(q?.high), cls: trendClass(rel(q?.high)) },
        { label: '流通市值', value: fmtAmount(q?.circulateMarketValue) },
        { label: '换手率', value: q?.turnoverRate == null ? '--' : `${q.turnoverRate.toFixed(2)}%` },
        { label: '最低价', value: fmtPrice(q?.low), cls: trendClass(rel(q?.low)) },
        { label: '市盈率', value: q?.pe == null ? '--' : q.pe.toFixed(2) },
        { label: '成交额', value: fmtAmount(q?.amount) }
    ]
})

// 操作按钮定义
const actions = [
    { kind: 'buy' as const, label: '买入', cls: 'is-buy' },
    { kind: 'sell' as const, label: '卖出', cls: 'is-sell' },
    { kind: 'cancel' as const, label: '撤单', cls: '' },
    { kind: 'detail' as const, label: '明细', cls: '' }
]
</script>

<template>
    <div class="stock-header">
        <div class="left">
            <div class="name-line">
                <span class="name">{{ quote?.name || detail?.name || '--' }}</span>
                <span class="code num">{{ quote?.code || detail?.code }}</span>
                <span v-if="detail?.board" class="board">{{ detail.board }}</span>
            </div>
            <div class="price num" :class="trendClass(quote?.changePercent)">
                {{ fmtPrice(quote?.price) }}
            </div>
            <div class="change" :class="trendClass(quote?.changePercent)">
                <span class="num">{{ fmtChange(quote?.change) }}</span>
                <span class="num">{{ fmtPercent(quote?.changePercent) }}</span>
            </div>
        </div>

        <div class="metrics">
            <div v-for="m in metrics" :key="m.label" class="metric">
                <span class="k">{{ m.label }}</span>
                <span class="v num" :class="m.cls">{{ m.value }}</span>
            </div>
        </div>

        <div class="actions">
            <button
                v-for="a in actions"
                :key="a.kind"
                class="circle"
                :class="a.cls"
                @click="emit('action', a.kind)"
            >
                {{ a.label }}
            </button>
        </div>
    </div>
</template>

<style scoped>
.stock-header {
    display: flex;
    align-items: center;
    gap: 28px;
    padding: 12px 20px;
    background: var(--bg-panel);
    border: 1px solid var(--border);
    border-radius: var(--radius);
}

.left {
    flex: 0 0 196px;
    display: flex;
    flex-direction: column;
}

.name-line {
    display: flex;
    align-items: baseline;
    gap: 6px;
    margin-bottom: 2px;
}

.name {
    font-size: 15px;
    font-weight: 600;
    color: var(--text-primary);
}

.code {
    font-size: 13px;
    color: var(--text-secondary);
}

.board {
    font-size: 12px;
    color: var(--text-secondary);
    border: 1px solid var(--border-strong);
    border-radius: 2px;
    padding: 0 3px;
    line-height: 14px;
}

.price {
    font-size: 38px;
    font-weight: 700;
    line-height: 1.05;
    letter-spacing: -0.5px;
}

.change {
    display: flex;
    align-items: baseline;
    gap: 10px;
    margin-top: 2px;
    font-size: 15px;
    font-weight: 600;
}

.metrics {
    flex: 1 1 auto;
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 6px 26px;
    max-width: 520px;
}

.metric {
    display: flex;
    align-items: baseline;
    gap: 8px;
    font-size: 14px;
    white-space: nowrap;
}

.metric .k {
    color: var(--text-secondary);
    min-width: 46px;
}

.metric .v {
    color: var(--text-primary);
}

.actions {
    flex: 0 0 auto;
    display: flex;
    align-items: center;
    gap: 20px;
}

.circle {
    width: 62px;
    height: 62px;
    border-radius: 50%;
    border: 1px solid var(--border-strong);
    background: var(--bg-panel);
    font-size: 15px;
    color: var(--text-regular);
    cursor: pointer;
    transition: all 0.16s;
}

.circle:hover {
    border-color: var(--text-secondary);
    background: var(--bg-hover);
}

.circle.is-buy {
    color: var(--up);
    border-color: rgba(229, 72, 77, 0.35);
}

.circle.is-buy:hover {
    background: var(--up-weak);
    border-color: var(--up);
}

.circle.is-sell {
    color: var(--down);
    border-color: rgba(0, 168, 112, 0.35);
}

.circle.is-sell:hover {
    background: var(--down-weak);
    border-color: var(--down);
}
</style>
