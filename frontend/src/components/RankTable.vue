<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { marketApi } from '@/api/market'
import { useMarketStore } from '@/stores/market'
import type { StockQuoteVO } from '@/types'
import { fmtAmount, fmtLots, fmtPercent, fmtPrice, trendClass } from '@/utils/format'
import { marketSocket } from '@/ws/marketSocket'

/**
 * 排行页签
 */
interface RankTab {
    key: string
    label: string
    // 排序指标
    sortBy: number
    // 排序方向
    order: number
}

// 页签定义
const tabs: RankTab[] = [
    { key: 'up', label: '涨幅榜', sortBy: 2, order: 2 },
    { key: 'down', label: '跌幅榜', sortBy: 2, order: 1 },
    { key: 'amount', label: '成交额', sortBy: 4, order: 2 },
    { key: 'turnover', label: '换手率', sortBy: 5, order: 2 },
    { key: 'mcap', label: '总市值', sortBy: 7, order: 2 }
]

const router = useRouter()
const market = useMarketStore()

// 当前页签
const active = ref('up')
// 当前页记录
const rows = ref<StockQuoteVO[]>([])
// 记录总数
const total = ref(0)
// 当前页码
const pageNum = ref(1)
// 每页条数
const pageSize = ref(20)
// 加载状态
const loading = ref(false)
// 错误信息
const error = ref('')

// 当前页签定义
const activeTab = computed(() => tabs.find((t) => t.key === active.value) ?? tabs[0])

// 取消订阅函数
let offSub: (() => void) | null = null

/**
 * 加载排行榜
 */
async function load(): Promise<void> {
    loading.value = true
    error.value = ''
    try {
        const page = await marketApi.rank({
            sortBy: activeTab.value.sortBy,
            order: activeTab.value.order,
            pageNum: pageNum.value,
            pageSize: pageSize.value
        })
        rows.value = page.records
        total.value = page.total
        subscribe()
    } catch (e) {
        error.value = e instanceof Error ? e.message : '排行榜加载失败'
        rows.value = []
        total.value = 0
    } finally {
        loading.value = false
    }
}

/**
 * 订阅当前页行情
 */
function subscribe(): void {
    offSub?.()
    offSub = null
    if (!rows.value.length) return
    offSub = marketSocket.subscribe(rows.value.map((r) => r.code), {
        quote: (q) => {
            const i = rows.value.findIndex((r) => r.code === q.code)
            if (i >= 0) rows.value[i] = { ...rows.value[i], ...q }
        }
    })
}

/**
 * 切换页签
 * @param key 页签标识
 */
function switchTab(key: string): void {
    if (active.value === key) return
    active.value = key
    pageNum.value = 1
    void load()
}

/**
 * 切换页码
 * @param p 页码
 */
function onPageChange(p: number): void {
    pageNum.value = p
    void load()
}

/**
 * 打开个股详情页
 * @param row 行情快照
 */
function openStock(row: StockQuoteVO): void {
    void router.push({ name: 'stock-detail', params: { code: row.code } })
}

/**
 * 添加或移除自选股
 * @param row 行情快照
 * @param ev 点击事件
 */
async function toggleWatch(row: StockQuoteVO, ev: Event): Promise<void> {
    ev.stopPropagation()
    if (market.isWatched(row.code)) {
        await market.removeFromWatchlist(row.code)
    } else {
        await market.addToWatchlist(row.code)
    }
}

onMounted(load)

watch(pageSize, () => {
    pageNum.value = 1
    void load()
})

onBeforeUnmount(() => {
    offSub?.()
    offSub = null
})
</script>

<template>
    <div class="rank panel">
        <div class="rank-head">
            <div class="tabs">
                <button
                    v-for="t in tabs"
                    :key="t.key"
                    class="tab"
                    :class="{ active: active === t.key }"
                    @click="switchTab(t.key)"
                >
                    {{ t.label }}
                </button>
            </div>
            <div class="head-right">
                <span class="total">共{{ total.toLocaleString('en-US') }}只</span>
                <el-button text size="small" :loading="loading" @click="load">
                    <el-icon><Refresh /></el-icon>
                </el-button>
            </div>
        </div>

        <div class="rank-body">
            <el-table
                v-loading="loading"
                :data="rows"
                :row-key="(r: StockQuoteVO) => r.code"
                size="small"
                height="100%"
                style="width: 100%"
                :empty-text="error || '暂无行情数据'"
                @row-click="openStock"
            >
                <el-table-column prop="code" label="代码" width="76">
                    <template #default="{ row }">
                        <span class="num code">{{ row.code }}</span>
                    </template>
                </el-table-column>

                <el-table-column prop="name" label="名称" min-width="92" show-overflow-tooltip />

                <el-table-column label="最新价" width="84" align="right">
                    <template #default="{ row }">
                        <span class="num" :class="trendClass(row.changePercent)">
                            {{ fmtPrice(row.price) }}
                        </span>
                    </template>
                </el-table-column>

                <el-table-column label="涨跌幅" width="88" align="right">
                    <template #default="{ row }">
                        <span class="num strong" :class="trendClass(row.changePercent)">
                            {{ fmtPercent(row.changePercent) }}
                        </span>
                    </template>
                </el-table-column>

                <el-table-column label="成交额" width="98" align="right">
                    <template #default="{ row }">
                        <span class="num amount">{{ fmtAmount(row.amount) }}</span>
                    </template>
                </el-table-column>

                <el-table-column label="换手率" width="84" align="right">
                    <template #default="{ row }">
                        <span class="num amount">
                            {{ row.turnoverRate == null ? '--' : `${row.turnoverRate.toFixed(2)}%` }}
                        </span>
                    </template>
                </el-table-column>

                <el-table-column label="成交量" width="92" align="right">
                    <template #default="{ row }">
                        <span class="num amount">{{ fmtLots(row.volume) }}</span>
                    </template>
                </el-table-column>

                <el-table-column width="46" align="center">
                    <template #default="{ row }">
                        <button
                            class="star"
                            :class="{ on: market.isWatched(row.code) }"
                            :title="market.isWatched(row.code) ? '取消自选' : '加入自选'"
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

        <div class="rank-foot">
            <el-pagination
                small
                layout="prev, pager, next"
                :total="total"
                :page-size="pageSize"
                :current-page="pageNum"
                :pager-count="5"
                @current-change="onPageChange"
            />
        </div>
    </div>
</template>

<style scoped>
.rank {
    display: flex;
    flex-direction: column;
    min-height: 0;
    overflow: hidden;
}

.rank-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 42px;
    padding: 0 8px 0 4px;
    border-bottom: 1px solid var(--divider);
}

.tabs {
    display: flex;
    align-items: center;
    gap: 2px;
}

.tab {
    height: 30px;
    padding: 0 12px;
    border: none;
    border-radius: 4px;
    background: transparent;
    font-size: 15px;
    color: var(--text-regular);
    cursor: pointer;
    transition: background 0.15s, color 0.15s;
}

.tab:hover {
    background: var(--bg-hover);
}

.tab.active {
    background: var(--primary-weak);
    color: var(--primary);
    font-weight: 600;
}

.head-right {
    display: flex;
    align-items: center;
    gap: 6px;
}

.total {
    font-size: 14px;
    color: var(--text-secondary);
}

.rank-body {
    flex: 1 1 auto;
    min-height: 0;
}

.rank-body :deep(.el-table__row) {
    cursor: pointer;
}

.code {
    color: var(--text-regular);
    font-size: 14px;
}

.strong {
    font-weight: 600;
}

.amount {
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
    transition: color 0.15s, background 0.15s;
}

.star:hover {
    background: var(--bg-hover);
    color: var(--primary);
}

.star.on {
    color: #f5a524;
}

.rank-foot {
    display: flex;
    justify-content: flex-end;
    padding: 6px 10px;
    border-top: 1px solid var(--divider);
}
</style>
