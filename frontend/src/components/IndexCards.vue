<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useMarketStore } from '@/stores/market'
import { fmtAmount, fmtChange, fmtPercent, trendClass } from '@/utils/format'

// 行情仓库
const market = useMarketStore()

// 加载状态
const loading = ref(false)
// 轮询定时器句柄
let timer: number | null = null

/**
 * 拉取指数行情
 */
async function refresh(): Promise<void> {
    if (loading.value) return
    loading.value = true
    try {
        await market.loadIndexes()
    } finally {
        loading.value = false
    }
}

onMounted(() => {
    void refresh()
    timer = window.setInterval(refresh, 5000)
})

onBeforeUnmount(() => {
    if (timer !== null) window.clearInterval(timer)
    timer = null
})
</script>

<template>
    <div class="index-cards">
        <template v-if="market.indexes.length">
            <div
                v-for="idx in market.indexes"
                :key="idx.code"
                class="card"
                :class="trendClass(idx.change)"
            >
                <div class="row-top">
                    <span class="name">{{ idx.name }}</span>
                    <span class="code">{{ idx.code }}</span>
                </div>

                <template v-if="!idx.volume">
                    <div class="price num flat">--</div>
                    <div class="row-bottom">
                        <span class="chg flat">暂无成交</span>
                    </div>
                    <div class="meta">成分股今日无成交</div>
                </template>

                <template v-else>
                    <div class="price num" :class="trendClass(idx.change)">
                        {{ idx.price?.toFixed(2) ?? '--' }}
                    </div>
                    <div class="row-bottom">
                        <span class="chg num" :class="trendClass(idx.change)">
                            {{ fmtChange(idx.change) }}
                        </span>
                        <span class="pct num" :class="trendClass(idx.change)">
                            {{ fmtPercent(idx.changePercent) }}
                        </span>
                    </div>
                    <div class="meta">成交{{ fmtAmount(idx.amount) }}</div>
                </template>
            </div>
        </template>

        <template v-else-if="!market.indexesError">
            <div v-for="i in 5" :key="i" class="card skeleton">
                <div class="skeleton-line short" />
                <div class="skeleton-line tall" />
                <div class="skeleton-line" />
            </div>
        </template>

        <div v-else class="card error-card">
            <span class="err-text">{{ market.indexesError }}</span>
            <button class="retry" @click="refresh">重新加载</button>
        </div>
    </div>
</template>

<style scoped>
.index-cards {
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: 10px;
}

.card {
    position: relative;
    padding: 14px 16px;
    background: var(--bg-panel);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    overflow: hidden;
    min-height: 108px;
}

.card::before {
    content: '';
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    width: 3px;
    background: var(--flat);
}

.card.up::before {
    background: var(--up);
}

.card.down::before {
    background: var(--down);
}

.row-top {
    display: flex;
    align-items: baseline;
    gap: 6px;
}

.name {
    font-size: 15px;
    font-weight: 600;
    color: var(--text-primary);
}

.code {
    font-size: 13px;
    color: var(--text-placeholder);
    font-family: var(--font-num);
}

.price {
    margin-top: 6px;
    font-size: 26px;
    font-weight: 600;
    line-height: 1.2;
}

.row-bottom {
    display: flex;
    align-items: baseline;
    gap: 8px;
    margin-top: 2px;
    font-size: 14px;
}

.meta {
    margin-top: 6px;
    font-size: 13px;
    color: var(--text-placeholder);
}

.skeleton {
    display: flex;
    flex-direction: column;
    gap: 9px;
    justify-content: center;
}

.skeleton-line {
    height: 9px;
    border-radius: 3px;
    background: var(--bg-hover);
    animation: shimmer 1.2s ease-in-out infinite;
}

.skeleton-line.short {
    width: 45%;
}

.skeleton-line.tall {
    height: 18px;
    width: 65%;
}

@keyframes shimmer {
    0%,
    100% {
        opacity: 1;
    }
    50% {
        opacity: 0.45;
    }
}

.error-card {
    grid-column: 1 / -1;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
}

.err-text {
    font-size: 14px;
    color: var(--text-secondary);
}

.retry {
    height: 26px;
    padding: 0 12px;
    border: 1px solid var(--border-strong);
    border-radius: 4px;
    background: var(--bg-panel);
    font-size: 14px;
    color: var(--text-regular);
    cursor: pointer;
}

.retry:hover {
    border-color: var(--primary);
    color: var(--primary);
}
</style>
