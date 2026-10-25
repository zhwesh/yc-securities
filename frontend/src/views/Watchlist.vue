<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { marketApi } from '@/api/market'
import { useMarketStore } from '@/stores/market'
import type { StockQuoteVO } from '@/types'
import { fmtAmount, fmtChange, fmtPercent, fmtPrice, trendClass } from '@/utils/format'
import { marketSocket } from '@/ws/marketSocket'

const router = useRouter()
const market = useMarketStore()

const input = ref('')
const adding = ref(false)
const loading = ref(false)

let offSub: (() => void) | null = null

interface Row {
    code: string
    name: string
    quote: StockQuoteVO | null
}

const rows = computed<Row[]>(() =>
    market.watchlist.map((s) => ({
        code: s.code,
        name: market.quotes[s.code]?.name || s.name,
        quote: market.quotes[s.code] ?? null
    }))
)

const maxReached = computed(() => market.watchlist.length >= 50)

function subscribe(): void {
    offSub?.()
    offSub = null
    const codes = market.watchCodes
    if (!codes.length) return
    offSub = marketSocket.subscribe(
        codes,
        {
            quote: (q) => market.applyQuote(q)
        },
        { priority: true }
    )
}

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
        /* 拦截器已提示 */
    } finally {
        adding.value = false
    }
}

async function remove(row: Row): Promise<void> {
    await market.removeFromWatchlist(row.code)
    subscribe()
    ElMessage.success(`已移除 ${row.name}`)
}

async function move(row: Row, delta: number): Promise<void> {
    const codes = [...market.watchCodes]
    const i = codes.indexOf(row.code)
    const j = i + delta
    if (i < 0 || j < 0 || j >= codes.length) return
    ;[codes[i], codes[j]] = [codes[j], codes[i]]
    await market.reorderWatchlist(codes)
}

function open(code: string): void {
    void router.push({ name: 'stock-detail', params: { code } })
}

onMounted(async () => {
    if (!market.watchlistLoaded) await market.loadWatchlist()
    await market.loadWatchQuotes()
    subscribe()
})

onBeforeUnmount(() => {
    offSub?.()
    offSub = null
})
</script>

<template>
    <div class="watchlist-page">
        <section class="panel">
            <div class="head">
                <div class="title">
                    自选股
                    <span class="count">{{ market.watchlist.length }}/50</span>
                </div>
                <div class="head-actions">
                    <el-input
                        v-model="input"
                        size="small"
                        placeholder="输入6位代码"
                        maxlength="6"
                        class="add-input"
                        @keyup.enter="add"
                    >
                        <template #prefix><el-icon><Plus /></el-icon></template>
                    </el-input>
                    <el-button size="small" type="primary" :loading="adding" :disabled="maxReached" @click="add">
                        添加
                    </el-button>
                    <el-button size="small" :loading="loading" @click="refresh">
                        <el-icon><Refresh /></el-icon>
                    </el-button>
                </div>
            </div>

            <el-table
                v-loading="loading"
                :data="rows"
                row-key="code"
                size="small"
                height="100%"
                style="width: 100%"
                empty-text="还没有自选股，可在「大盘」排行榜点击 ☆ 添加，或用上方的代码添加"
                @row-click="(row: Row) => open(row.code)"
            >
                <el-table-column type="index" label="#" width="52" align="center" />

                <el-table-column label="代码" width="88">
                    <template #default="{ row }">
                        <span class="num">{{ row.code }}</span>
                    </template>
                </el-table-column>

                <el-table-column label="名称" min-width="110" show-overflow-tooltip>
                    <template #default="{ row }">{{ row.name }}</template>
                </el-table-column>

                <el-table-column label="最新价" width="96" align="right">
                    <template #default="{ row }">
                        <span class="num strong" :class="trendClass(row.quote?.changePercent)">
                            {{ fmtPrice(row.quote?.price) }}
                        </span>
                    </template>
                </el-table-column>

                <el-table-column label="涨跌额" width="92" align="right">
                    <template #default="{ row }">
                        <span class="num" :class="trendClass(row.quote?.change)">
                            {{ fmtChange(row.quote?.change) }}
                        </span>
                    </template>
                </el-table-column>

                <el-table-column label="涨跌幅" width="96" align="right">
                    <template #default="{ row }">
                        <span class="num strong" :class="trendClass(row.quote?.changePercent)">
                            {{ fmtPercent(row.quote?.changePercent) }}
                        </span>
                    </template>
                </el-table-column>

                <el-table-column label="成交额" width="110" align="right">
                    <template #default="{ row }">
                        <span class="num dim">{{ fmtAmount(row.quote?.amount) }}</span>
                    </template>
                </el-table-column>

                <el-table-column label="换手率" width="90" align="right">
                    <template #default="{ row }">
                        <span class="num dim">
                            {{ row.quote?.turnoverRate == null ? '--' : `${row.quote.turnoverRate.toFixed(2)}%` }}
                        </span>
                    </template>
                </el-table-column>

                <el-table-column label="操作" width="176" align="center">
                    <template #default="{ row, $index }">
                        <div class="ops" @click.stop>
                            <el-button text size="small" :disabled="$index === 0" @click="move(row, -1)">
                                上移
                            </el-button>
                            <el-button
                                text
                                size="small"
                                :disabled="$index === rows.length - 1"
                                @click="move(row, 1)"
                            >
                                下移
                            </el-button>
                            <el-button text size="small" type="danger" @click="remove(row)">删除</el-button>
                        </div>
                    </template>
                </el-table-column>
            </el-table>
        </section>
    </div>
</template>

<style scoped>
.watchlist-page {
    height: 100%;
    min-height: 0;
}

.panel {
    display: flex;
    flex-direction: column;
    height: 100%;
    min-height: 0;
    overflow: hidden;
}

.head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    height: 52px;
    padding: 0 14px;
    border-bottom: 1px solid var(--divider);
    flex: 0 0 auto;
}

.title {
    font-size: 16px;
    font-weight: 600;
    color: var(--text-primary);
}

.count {
    margin-left: 6px;
    font-size: 13px;
    font-weight: 400;
    color: var(--text-placeholder);
}

.head-actions {
    display: flex;
    align-items: center;
    gap: 8px;
}

.add-input {
    width: 170px;
}

.num {
    font-variant-numeric: tabular-nums;
}

.strong {
    font-weight: 600;
}

.dim {
    color: var(--text-regular);
}

.ops {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 0;
}

.ops :deep(.el-button) {
    padding: 0 5px;
    font-size: 14px;
}

:deep(.el-table__row) {
    cursor: pointer;
}
</style>
