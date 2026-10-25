<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { marketApi, stockApi } from '@/api/market'
import { useMarketStore } from '@/stores/market'
import type { StockQuoteVO } from '@/types'
import { fmtAmount, fmtLots, fmtPercent, fmtPrice, trendClass } from '@/utils/format'

const router = useRouter()
const market = useMarketStore()

/** 列名 -> /market/rank 的 sortBy */
const SORT_FIELD: Record<string, number> = {
    price: 1,
    changePercent: 2,
    volume: 3,
    amount: 4,
    turnoverRate: 5,
    pb: 6,
    marketValue: 7,
    circulateMarketValue: 8
}

const presets = [
    { label: '涨幅榜', sortBy: 2, order: 2 },
    { label: '跌幅榜', sortBy: 2, order: 1 },
    { label: '成交额榜', sortBy: 4, order: 2 },
    { label: '换手率榜', sortBy: 5, order: 2 },
    { label: '总市值榜', sortBy: 7, order: 2 },
    { label: '低价榜', sortBy: 1, order: 1 }
]

const sortBy = ref(2)
const order = ref(2)
const pageNum = ref(1)
const pageSize = ref(50)
const total = ref(0)
const rows = ref<StockQuoteVO[]>([])
const loading = ref(false)
const error = ref('')

const keyword = ref('')
const kwMode = ref(false)

const activePreset = computed(() =>
    presets.find((p) => p.sortBy === sortBy.value && p.order === order.value)
)

function applySort(list: StockQuoteVO[]): StockQuoteVO[] {
    const key = Object.keys(SORT_FIELD).find((k) => SORT_FIELD[k] === sortBy.value)
    if (!key) return list
    const asc = order.value === 1
    return [...list].sort((a, b) => {
        const av = a[key as keyof StockQuoteVO] as number | null
        const bv = b[key as keyof StockQuoteVO] as number | null
        if (av == null) return 1
        if (bv == null) return -1
        return asc ? av - bv : bv - av
    })
}

async function loadRank(): Promise<void> {
    loading.value = true
    error.value = ''
    try {
        const page = await marketApi.rank({
            sortBy: sortBy.value,
            order: order.value,
            pageNum: pageNum.value,
            pageSize: pageSize.value
        })
        rows.value = page.records
        total.value = page.total
    } catch (e) {
        error.value = e instanceof Error ? e.message : '加载失败'
        rows.value = []
        total.value = 0
    } finally {
        loading.value = false
    }
}

async function loadKeyword(): Promise<void> {
    loading.value = true
    error.value = ''
    try {
        const page = await stockApi.list(keyword.value.trim(), 1, 50)
        const codes = page.records.map((s) => s.code)
        if (!codes.length) {
            rows.value = []
            total.value = 0
            return
        }
        const quotes = await marketApi.quotes(codes)
        const byCode = new Map(quotes.map((q) => [q.code, q]))
        const list = page.records
            .map((s) => byCode.get(s.code))
            .filter((q): q is StockQuoteVO => !!q)
        rows.value = applySort(list)
        total.value = page.total
    } catch (e) {
        error.value = e instanceof Error ? e.message : '搜索失败'
        rows.value = []
        total.value = 0
    } finally {
        loading.value = false
    }
}

function reload(): void {
    if (kwMode.value) void loadKeyword()
    else void loadRank()
}

function applyPreset(p: { sortBy: number; order: number }): void {
    sortBy.value = p.sortBy
    order.value = p.order
    pageNum.value = 1
    reload()
}

function onSortChange({ prop, order: dir }: { prop: string | null; order: string | null }): void {
    if (!prop || !dir) return
    const field = SORT_FIELD[prop]
    if (!field) return
    sortBy.value = field
    order.value = dir === 'ascending' ? 1 : 2
    pageNum.value = 1
    if (kwMode.value) {
        rows.value = applySort(rows.value)
    } else {
        void loadRank()
    }
}

function doSearch(): void {
    const kw = keyword.value.trim()
    if (!kw) {
        kwMode.value = false
        pageNum.value = 1
        void loadRank()
        return
    }
    kwMode.value = true
    void loadKeyword()
}

function clearSearch(): void {
    keyword.value = ''
    kwMode.value = false
    pageNum.value = 1
    void loadRank()
}

function open(row: StockQuoteVO): void {
    void router.push({ name: 'stock-detail', params: { code: row.code } })
}

async function toggleWatch(row: StockQuoteVO, ev: Event): Promise<void> {
    ev.stopPropagation()
    if (market.isWatched(row.code)) await market.removeFromWatchlist(row.code)
    else await market.addToWatchlist(row.code)
}

function onPageChange(p: number): void {
    pageNum.value = p
    void loadRank()
}

function onSizeChange(s: number): void {
    pageSize.value = s
    pageNum.value = 1
    void loadRank()
}

onMounted(loadRank)
</script>

<template>
    <div class="screener">
        <section class="panel">
            <div class="head">
                <div class="presets">
                    <button
                        v-for="p in presets"
                        :key="p.label"
                        class="preset"
                        :class="{ active: activePreset?.label === p.label && !kwMode }"
                        @click="applyPreset(p)"
                    >
                        {{ p.label }}
                    </button>
                </div>

                <div class="search">
                    <el-input
                        v-model="keyword"
                        size="small"
                        placeholder="代码 / 名称前缀"
                        clearable
                        class="search-input"
                        @keyup.enter="doSearch"
                        @clear="clearSearch"
                    >
                        <template #prefix><el-icon><Search /></el-icon></template>
                    </el-input>
                    <el-button size="small" @click="doSearch">搜索</el-button>
                </div>
            </div>

            <div class="hint-bar">
                <template v-if="kwMode">
                    搜索「{{ keyword }}」共 <b>{{ total }}</b> 条，展示前{{ rows.length }}条；点击表头可排序
                </template>
                <template v-else>
                    共 <b>{{ total.toLocaleString('en-US') }}</b> 只股票 · 按「涨跌幅」降序排列 ·
                    点击表头切换排序 · 第{{ pageNum }}页
                </template>
            </div>

            <div class="table-wrap">
                <el-table
                    v-loading="loading"
                    :data="rows"
                    row-key="code"
                    size="small"
                    :height="kwMode ? undefined : '100%'"
                    style="width: 100%"
                    :empty-text="error || '没有符合条件的股票'"
                    @sort-change="onSortChange"
                    @row-click="open"
                >
                    <el-table-column label="代码" width="88">
                        <template #default="{ row }">
                            <span class="num">{{ row.code }}</span>
                        </template>
                    </el-table-column>

                    <el-table-column label="名称" min-width="100" show-overflow-tooltip>
                        <template #default="{ row }">{{ row.name }}</template>
                    </el-table-column>

                    <el-table-column prop="price" label="最新价" width="100" align="right" sortable="custom">
                        <template #default="{ row }">
                            <span class="num strong" :class="trendClass(row.changePercent)">
                                {{ fmtPrice(row.price) }}
                            </span>
                        </template>
                    </el-table-column>

                    <el-table-column
                        prop="changePercent"
                        label="涨跌幅"
                        width="104"
                        align="right"
                        sortable="custom"
                    >
                        <template #default="{ row }">
                            <span class="num strong" :class="trendClass(row.changePercent)">
                                {{ fmtPercent(row.changePercent) }}
                            </span>
                        </template>
                    </el-table-column>

                    <el-table-column prop="volume" label="成交量" width="104" align="right" sortable="custom">
                        <template #default="{ row }">
                            <span class="num dim">{{ fmtLots(row.volume) }}</span>
                        </template>
                    </el-table-column>

                    <el-table-column prop="amount" label="成交额" width="110" align="right" sortable="custom">
                        <template #default="{ row }">
                            <span class="num dim">{{ fmtAmount(row.amount) }}</span>
                        </template>
                    </el-table-column>

                    <el-table-column
                        prop="turnoverRate"
                        label="换手率"
                        width="100"
                        align="right"
                        sortable="custom"
                    >
                        <template #default="{ row }">
                            <span class="num dim">
                                {{ row.turnoverRate == null ? '--' : `${row.turnoverRate.toFixed(2)}%` }}
                            </span>
                        </template>
                    </el-table-column>

                    <el-table-column prop="pb" label="市净率" width="92" align="right" sortable="custom">
                        <template #default="{ row }">
                            <span class="num dim">{{ row.pb == null ? '--' : row.pb.toFixed(2) }}</span>
                        </template>
                    </el-table-column>

                    <el-table-column
                        prop="marketValue"
                        label="总市值"
                        width="110"
                        align="right"
                        sortable="custom"
                    >
                        <template #default="{ row }">
                            <span class="num dim">{{ fmtAmount(row.marketValue) }}</span>
                        </template>
                    </el-table-column>

                    <el-table-column
                        prop="circulateMarketValue"
                        label="流通市值"
                        width="112"
                        align="right"
                        sortable="custom"
                    >
                        <template #default="{ row }">
                            <span class="num dim">{{ fmtAmount(row.circulateMarketValue) }}</span>
                        </template>
                    </el-table-column>

                    <el-table-column width="52" align="center">
                        <template #default="{ row }">
                            <button
                                class="star"
                                :class="{ on: market.isWatched(row.code) }"
                                @click="toggleWatch(row, $event)"
                            >
                                <el-icon>
                                    <StarFilled v-if="market.isWatched(row.code)" />
                                    <Star v-else />
                                </el-icon>
                            </button>
                        </template>
                    </el-table-column>
                </el-table>
            </div>

            <div v-if="!kwMode" class="foot">
                <el-pagination
                    small
                    layout="total, sizes, prev, pager, next, jumper"
                    :total="total"
                    :page-size="pageSize"
                    :current-page="pageNum"
                    :page-sizes="[20, 50, 100]"
                    :pager-count="5"
                    @current-change="onPageChange"
                    @size-change="onSizeChange"
                />
            </div>
        </section>
    </div>
</template>

<style scoped>
.screener {
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
    padding: 10px 14px;
    border-bottom: 1px solid var(--divider);
    flex: 0 0 auto;
}

.presets {
    display: flex;
    gap: 6px;
    flex-wrap: wrap;
}

.preset {
    height: 28px;
    padding: 0 13px;
    border: 1px solid var(--border);
    border-radius: 4px;
    background: var(--bg-panel);
    font-size: 14px;
    color: var(--text-regular);
    cursor: pointer;
    transition: all 0.15s;
}

.preset:hover {
    border-color: var(--primary);
    color: var(--primary);
}

.preset.active {
    background: var(--primary-weak);
    border-color: var(--primary);
    color: var(--primary);
    font-weight: 600;
}

.search {
    display: flex;
    gap: 6px;
    align-items: center;
}

.search-input {
    width: 190px;
}

.hint-bar {
    padding: 6px 14px;
    font-size: 13px;
    color: var(--text-secondary);
    background: var(--bg-subtle);
    border-bottom: 1px solid var(--divider);
    flex: 0 0 auto;
}

.hint-bar b {
    color: var(--text-primary);
}

.table-wrap {
    flex: 1 1 auto;
    min-height: 0;
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

.star {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 22px;
    height: 22px;
    border: none;
    border-radius: 4px;
    background: transparent;
    color: var(--text-placeholder);
    cursor: pointer;
    font-size: 15px;
}

.star:hover {
    color: var(--primary);
    background: var(--bg-hover);
}

.star.on {
    color: #f5a524;
}

.foot {
    display: flex;
    justify-content: flex-end;
    padding: 6px 10px;
    border-top: 1px solid var(--divider);
    flex: 0 0 auto;
}

:deep(.el-table__row) {
    cursor: pointer;
}
</style>
