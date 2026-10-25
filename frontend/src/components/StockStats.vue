<script setup lang="ts">
import { computed } from 'vue'
import type { StockDetailVO, StockQuoteVO } from '@/types'
import { fmtDate, fmtLots, fmtPrice } from '@/utils/format'

const props = defineProps<{
    quote: StockQuoteVO | null
    detail: StockDetailVO | null
}>()

/**
 * 格式化股本数量
 * @param v 股数
 * @returns 以亿股或万股为单位的字符串
 */
function fmtShares(v: number | null | undefined): string {
    if (v == null || !Number.isFinite(v)) return '--'
    const abs = Math.abs(v)
    if (abs >= 1e8) return `${(v / 1e8).toFixed(2)}亿股`
    if (abs >= 1e4) return `${(v / 1e4).toFixed(2)}万股`
    return `${v}股`
}

// 统计项列表
const stats = computed(() => {
    const q = props.quote
    const d = props.detail
    return [
        { label: '总股本', value: fmtShares(d?.totalShares) },
        { label: '流通股本', value: fmtShares(d?.circulateShares) },
        { label: '每股净资产', value: fmtPrice(d?.bookValue) },
        { label: '每股收益', value: fmtPrice(d?.eps) },
        { label: '发行价', value: fmtPrice(d?.initialPrice) },
        { label: '上市日期', value: fmtDate(d?.listDate) },
        { label: '所属板块', value: d?.board ?? '--' },
        {
            label: '振幅',
            value: q?.amplitude == null ? '--' : `${q.amplitude.toFixed(2)}%`
        },
        { label: '昨收', value: fmtPrice(q?.preClose) },
        { label: '成交量', value: fmtLots(q?.volume) }
    ]
})
</script>

<template>
    <div class="stock-stats">
        <div v-for="s in stats" :key="s.label" class="stat">
            <span class="k">{{ s.label }}</span>
            <span class="v num">{{ s.value }}</span>
        </div>
    </div>
</template>

<style scoped>
.stock-stats {
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: 8px 24px;
    padding: 12px 18px;
    background: var(--bg-panel);
    border: 1px solid var(--border);
    border-radius: var(--radius);
}

.stat {
    display: flex;
    align-items: baseline;
    gap: 8px;
    font-size: 14px;
    white-space: nowrap;
    overflow: hidden;
}

.stat .k {
    color: var(--text-secondary);
    flex: 0 0 auto;
}

.stat .v {
    color: var(--text-primary);
    overflow: hidden;
    text-overflow: ellipsis;
}
</style>
