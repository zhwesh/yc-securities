<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { marketApi } from '@/api/market'
import type { CallAuctionVO } from '@/types'
import { AuctionPhase } from '@/types'
import { fmtLots, fmtPrice } from '@/utils/format'

const props = defineProps<{ code: string }>()

// 竞价快照
const data = ref<CallAuctionVO | null>(null)
// 加载状态
const loading = ref(false)
// 错误信息
const error = ref('')

// 轮询定时器句柄
let timer: number | null = null

/**
 * 获取指定阶段的轮询间隔
 * @param phase 竞价阶段
 * @returns 间隔毫秒数
 */
function intervalOf(phase: number | undefined): number {
    if (phase === AuctionPhase.Closed) return 15000
    if (phase === AuctionPhase.Continuous) return 10000
    return 1000
}

// 是否处于竞价阶段
const isBidding = computed(() => {
    const p = data.value?.phase
    return (
        p === AuctionPhase.OpenBidding ||
        p === AuctionPhase.OpenBiddingLocked ||
        p === AuctionPhase.OpenFrozen
    )
})

// 虚拟成交价样式类名
const priceCls = computed(() => {
    const d = data.value
    if (!d?.indicativePrice) return 'flat'
    return 'up'
})

/**
 * 拉取竞价快照并按阶段安排下一次轮询
 */
async function load(): Promise<void> {
    if (!props.code) return
    loading.value = data.value === null
    try {
        data.value = await marketApi.auction(props.code)
        error.value = ''
    } catch (e) {
        error.value = e instanceof Error ? e.message : '竞价数据加载失败'
    } finally {
        loading.value = false
        schedule()
    }
}

/**
 * 安排下一次轮询
 */
function schedule(): void {
    if (timer !== null) window.clearTimeout(timer)
    timer = window.setTimeout(load, intervalOf(data.value?.phase))
}

onMounted(load)

onBeforeUnmount(() => {
    if (timer !== null) window.clearTimeout(timer)
    timer = null
})

watch(
    () => props.code,
    () => {
        data.value = null
        void load()
    }
)
</script>

<template>
    <div v-loading="loading && !data" class="auction">
        <div class="phase">
            <span class="phase-name">{{ data?.phaseName ?? '集合竞价' }}</span>
            <span v-if="isBidding" class="live">竞价中</span>
        </div>

        <div class="price-row">
            <span class="price num" :class="priceCls">
                {{ data?.indicativePrice != null ? fmtPrice(data.indicativePrice) : '--' }}
            </span>
            <span class="price-label">虚拟成交价</span>
        </div>

        <div class="grid">
            <div class="cell">
                <span class="k">虚拟成交量</span>
                <span class="v num">{{ fmtLots(data?.matchedVolume) }}</span>
            </div>
            <div class="cell">
                <span class="k">未匹配买量</span>
                <span class="v num up">{{ fmtLots(data?.unmatchedBuyVolume) }}</span>
            </div>
            <div class="cell">
                <span class="k">未匹配卖量</span>
                <span class="v num down">{{ fmtLots(data?.unmatchedSellVolume) }}</span>
            </div>
        </div>

        <div v-if="data?.asks?.length" class="book">
            <div v-for="(a, i) in [...data.asks].reverse()" :key="`a${i}`" class="row">
                <span class="lab">卖{{ data.asks.length - i }}</span>
                <span class="p num">{{ fmtPrice(a.price) }}</span>
                <span class="q num">{{ fmtLots(a.volume) }}</span>
            </div>
            <div v-for="(b, i) in data.bids" :key="`b${i}`" class="row">
                <span class="lab">买{{ i + 1 }}</span>
                <span class="p num">{{ fmtPrice(b.price) }}</span>
                <span class="q num">{{ fmtLots(b.volume) }}</span>
            </div>
        </div>

        <div v-if="!data?.asks?.length && !data?.bids?.length && !loading" class="empty">
            {{ error || '当前非集合竞价时段' }}
        </div>

        <div v-if="data?.updateTime" class="foot">
            更新于{{ data.updateTime.replace('T', ' ') }}
        </div>
    </div>
</template>

<style scoped>
.auction {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 10px;
    font-size: 14px;
}

.phase {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 6px;
}

.phase-name {
    font-size: 13px;
    color: var(--text-secondary);
}

.live {
    font-size: 12px;
    color: var(--up);
    border: 1px solid currentColor;
    border-radius: 3px;
    padding: 0 4px;
    line-height: 14px;
}

.price-row {
    display: flex;
    flex-direction: column;
    gap: 1px;
}

.price {
    font-size: 22px;
    font-weight: 700;
    line-height: 1.1;
}

.price-label {
    font-size: 12px;
    color: var(--text-placeholder);
}

.grid {
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding: 6px 0;
    border-top: 1px dashed var(--divider);
    border-bottom: 1px dashed var(--divider);
}

.cell {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 6px;
}

.cell .k {
    color: var(--text-secondary);
    font-size: 13px;
}

.cell .v {
    color: var(--text-primary);
}

.book {
    display: flex;
    flex-direction: column;
    gap: 1px;
}

.row {
    display: grid;
    grid-template-columns: 30px 1fr 1fr;
    align-items: center;
    gap: 4px;
    height: 18px;
}

.lab {
    font-size: 12px;
    color: var(--text-secondary);
}

.p,
.q {
    text-align: right;
    font-size: 13px;
}

.q {
    color: var(--text-regular);
}

.empty {
    font-size: 12px;
    line-height: 1.6;
    color: var(--text-placeholder);
}

.foot {
    font-size: 12px;
    color: var(--text-placeholder);
}
</style>
