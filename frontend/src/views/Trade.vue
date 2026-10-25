<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import OrderForm from '@/components/OrderForm.vue'
import OrderTable from '@/components/OrderTable.vue'
import PositionTable from '@/components/PositionTable.vue'
import StockSearch from '@/components/StockSearch.vue'
import TradeTable from '@/components/TradeTable.vue'
import { marketApi, stockApi } from '@/api/market'
import { useAccountStore } from '@/stores/account'
import { useTradeAuthStore } from '@/stores/tradeAuth'
import type { StockDetailVO, StockQuoteVO } from '@/types'
import { fmtMoney, trendClass } from '@/utils/format'
import { marketSocket } from '@/ws/marketSocket'

type TabKey = 'position' | 'pending' | 'all' | 'trade'

const route = useRoute()
const router = useRouter()
const account = useAccountStore()
const tradeAuth = useTradeAuthStore()

const tab = ref<TabKey>('position')
const selected = ref<{ code: string; name: string } | null>(null)
const quote = ref<StockQuoteVO | null>(null)
const detail = ref<StockDetailVO | null>(null)
const preferSide = ref<'buy' | 'sell'>('buy')
const ordersKey = ref(0)
const loadingQuote = ref(false)

let offSub: (() => void) | null = null

const tabs: { key: TabKey; label: string }[] = [
    { key: 'position', label: '持仓' },
    { key: 'pending', label: '当前委托' },
    { key: 'all', label: '全部委托' },
    { key: 'trade', label: '成交记录' }
]

/* ------------------------------- 账户 ------------------------------- */

const currentAccount = computed(() => account.current)

/** 后端没有总资产字段：总资产 = 可用资产 + Σ 持仓市值 */
const stats = computed(() => [
    {
        label: '总资产',
        value: fmtMoney(account.totalAssets),
        unit: '元',
        cls: ''
    },
    {
        label: '可用资产',
        value: fmtMoney(account.availableAssets),
        unit: '元',
        cls: ''
    },
    {
        label: '持仓市值',
        value: fmtMoney(account.positionMarketValue),
        unit: '元',
        cls: ''
    },
    {
        label: '浮动盈亏',
        value: fmtMoney(account.totalProfitLoss),
        unit: '元',
        cls: trendClass(account.totalProfitLoss)
    }
])

/* ------------------------------- 标的 ------------------------------- */

const board = computed(() => {
    if (detail.value?.board) return detail.value.board
    const c = selected.value?.code ?? ''
    if (/^(300|301)/.test(c)) return '创业板'
    if (/^(688|689)/.test(c)) return '科创板'
    if (/^(4|8)/.test(c)) return '北交所'
    return '主板'
})

const myPosition = computed(() => {
    const c = selected.value?.code
    if (!c) return null
    return account.positions.find((p) => p.code === c) ?? null
})

async function loadQuote(): Promise<void> {
    const c = selected.value?.code
    if (!c) {
        quote.value = null
        detail.value = null
        return
    }
    loadingQuote.value = true
    try {
        const [q, d] = await Promise.all([
            marketApi.quote(c),
            stockApi.detail(c).catch(() => null)
        ])
        quote.value = q
        detail.value = d
        if (selected.value) selected.value = { code: c, name: q.name || selected.value.name }
    } catch {
        quote.value = null
        detail.value = null
    } finally {
        loadingQuote.value = false
    }
}

function subscribe(): void {
    offSub?.()
    offSub = null
    const c = selected.value?.code
    if (!c) return
    offSub = marketSocket.subscribe([c], {
        quote: (q) => {
            if (q.code === c) quote.value = { ...(quote.value ?? ({} as StockQuoteVO)), ...q }
        }
    })
}

function pickStock(stock: { code: string; name: string }): void {
    selected.value = stock
    preferSide.value = 'buy'
    void loadQuote()
    subscribe()
}

/* ------------------------------- 操作 ------------------------------- */

async function verifyTradePwd(): Promise<void> {
    const id = account.currentId
    if (!id) return
    const ok = await tradeAuth.prompt(id)
    if (ok) {
        await account.loadPositions()
        ElMessage.success('交易密码已验证')
    }
}

function onSellRow(row: { code: string; stockName: string }): void {
    selected.value = { code: row.code, name: row.stockName }
    preferSide.value = 'sell'
    void loadQuote()
    subscribe()
}

/** 点持仓 / 委托 / 成交的任意一行，进该股票的详情页看行情 */
function openStock(row: { code?: string; stockCode?: string }): void {
    const code = row.code ?? row.stockCode
    if (!code) return
    void router.push({ name: 'stock-detail', params: { code } })
}

async function onSubmitted(): Promise<void> {
    await account.refreshAccount()
    if (account.currentId && tradeAuth.readOnline(account.currentId)) {
        await account.loadPositions()
    }
    ordersKey.value += 1
    tab.value = 'pending'
}

/* ------------------------------- 生命周期 ------------------------------- */

onMounted(async () => {
    if (!account.loaded) {
        try {
            await account.loadAccounts()
        } catch {
            /* ignore */
        }
    }
    await account.loadPositions()

    const fromQuery = typeof route.query.code === 'string' ? route.query.code : ''
    if (fromQuery) {
        selected.value = { code: fromQuery, name: fromQuery }
    } else if (account.positions.length) {
        const p = account.positions[0]
        selected.value = { code: p.code, name: p.stockName }
    }

    if (selected.value) {
        await loadQuote()
        subscribe()
    }
})

onBeforeUnmount(() => {
    offSub?.()
    offSub = null
})

watch(
    () => account.currentId,
    async () => {
        await account.loadPositions()
        ordersKey.value += 1
    }
)
</script>

<template>
    <div class="trade">
        <!-- 账户选择 -->
        <section class="account-bar panel">
            <div class="acc-left">
                <span class="label">资金账户</span>
                <div v-if="account.accounts.length" class="acc-tabs">
                    <button
                        v-for="a in account.accounts"
                        :key="a.id"
                        class="acc-tab"
                        :class="{ active: account.currentId === a.id }"
                        @click="account.selectAccount(a.id)"
                    >
                        <span class="acc-id num">#{{ a.id }}</span>
                        <span class="acc-amt num">{{ fmtMoney(a.availableAssets) }}</span>
                        <el-tag v-if="a.status === 2" size="small" type="danger" effect="plain">冻结</el-tag>
                    </button>
                </div>
                <span v-else-if="account.loaded" class="dim">尚未开通资金账户</span>
                <span v-else class="dim">加载中…</span>
            </div>

            <div class="acc-right">
                <template v-if="currentAccount">
                    <span class="boards">
                        <el-tag
                            v-for="b in currentAccount.boards"
                            :key="b"
                            size="small"
                            effect="plain"
                            class="board-tag"
                        >
                            {{ b }}
                        </el-tag>
                    </span>
                    <el-button size="small" text type="primary" @click="router.push('/assets')">
                        资金管理
                    </el-button>
                </template>
                <el-button v-else size="small" type="primary" @click="router.push('/assets')">
                    开通资金账户
                </el-button>
            </div>
        </section>

        <!-- 资产概览 -->
        <div class="stats">
            <div v-for="s in stats" :key="s.label" class="stat-card panel">
                <span class="stat-label">{{ s.label }}</span>
                <span class="stat-value num" :class="s.cls">{{ s.value }}</span>
                <span class="stat-unit">{{ s.unit }}</span>
            </div>
        </div>

        <div class="grid">
            <!-- 左：表格区 -->
            <section class="panel table-panel">
                <div class="panel-head">
                    <div class="tabs">
                        <button
                            v-for="t in tabs"
                            :key="t.key"
                            class="tab"
                            :class="{ active: tab === t.key }"
                            @click="tab = t.key"
                        >
                            {{ t.label }}
                        </button>
                    </div>
                    <el-button
                        size="small"
                        text
                        :loading="account.positionsLoading || loadingQuote"
                        @click="account.loadPositions()"
                    >
                        <el-icon><Refresh /></el-icon>
                    </el-button>
                </div>

                <div v-if="!account.current" class="empty-tip">
                    请先开通资金账户后查看持仓、委托与成交
                </div>

                <template v-else>
                    <div v-if="account.needTradeAuth" class="need-pwd">
                        <div class="need-pwd-text">
                            <el-icon><Lock /></el-icon>
                            查询持仓需要验证交易密码，验证后{{ tradeAuth.onlineHours }}小时内无需重复输入
                        </div>
                        <el-button size="small" type="primary" @click="verifyTradePwd">验证交易密码</el-button>
                    </div>

                    <div class="table-body">
                        <PositionTable
                            v-if="tab === 'position'"
                            :positions="account.positions"
                            :loading="account.positionsLoading"
                            clickable
                            @sell="onSellRow"
                            @open="openStock"
                        />
                        <OrderTable
                            v-else-if="tab === 'pending'"
                            :key="`pending-${ordersKey}`"
                            :account-id="account.currentId"
                            :status="1"
                            :auction-code="selected?.code"
                            clickable
                            show-pager
                            @open="openStock"
                        />
                        <OrderTable
                            v-else-if="tab === 'all'"
                            :key="`all-${ordersKey}`"
                            :account-id="account.currentId"
                            :auction-code="selected?.code"
                            clickable
                            show-pager
                            @open="openStock"
                        />
                        <TradeTable
                            v-else
                            :key="`trade-${ordersKey}`"
                            :account-id="account.currentId"
                            clickable
                            show-pager
                            @open="openStock"
                        />
                    </div>
                </template>
            </section>

            <!-- 右：下单 -->
            <section class="panel order-panel">
                <div class="panel-head order-head">
                    <span class="head-title">交易下单</span>
                    <StockSearch
                        :navigate="false"
                        placeholder="选择股票"
                        width="150px"
                        @select="pickStock"
                    />
                </div>

                <div v-if="!selected" class="empty-tip">
                    先选择一只股票，或从持仓里点「卖出」
                </div>

                <template v-else>
                    <div class="stock-line">
                        <div class="stock-name">
                            <span class="nm">{{ selected.name }}</span>
                            <span class="cd num">{{ selected.code }}</span>
                        </div>
                        <div class="stock-quote">
                            <span class="qp num" :class="trendClass(quote?.changePercent)">
                                {{ quote?.price != null ? quote.price.toFixed(2) : '--' }}
                            </span>
                            <span class="pc num" :class="trendClass(quote?.changePercent)">
                                {{ quote?.changePercent != null ? `${quote.changePercent.toFixed(2)}%` : '--' }}
                            </span>
                        </div>
                    </div>

                    <div v-if="!account.current" class="empty-tip">请先开通资金账户</div>

                    <OrderForm
                        v-else
                        :key="selected.code"
                        :code="selected.code"
                        :stock-name="selected.name"
                        :quote="quote"
                        :board="board"
                        :position="myPosition"
                        :default-side="preferSide"
                        @submitted="onSubmitted"
                    />
                </template>
            </section>
        </div>
    </div>
</template>

<style scoped>
.trade {
    display: flex;
    flex-direction: column;
    gap: 10px;
    height: 100%;
    min-height: 0;
}

/* ------------------------------ 账户条 ------------------------------ */

.account-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding: 8px 14px;
    flex: 0 0 auto;
}

.acc-left {
    display: flex;
    align-items: center;
    gap: 10px;
    min-width: 0;
}

.label {
    font-size: 14px;
    color: var(--text-secondary);
    white-space: nowrap;
}

.acc-tabs {
    display: flex;
    gap: 6px;
    overflow-x: auto;
}

.acc-tab {
    display: flex;
    align-items: center;
    gap: 8px;
    height: 30px;
    padding: 0 12px;
    border: 1px solid var(--border);
    border-radius: 4px;
    background: var(--bg-panel);
    cursor: pointer;
    font-size: 14px;
    color: var(--text-regular);
    white-space: nowrap;
    transition: all 0.15s;
}

.acc-tab:hover {
    border-color: var(--border-strong);
}

.acc-tab.active {
    border-color: var(--primary);
    background: var(--primary-weak);
    color: var(--primary);
}

.acc-id {
    font-size: 13px;
    color: var(--text-secondary);
}

.acc-tab.active .acc-id {
    color: var(--primary);
}

.acc-amt {
    font-weight: 600;
}

.acc-right {
    display: flex;
    align-items: center;
    gap: 10px;
    flex: 0 0 auto;
}

.boards {
    display: flex;
    gap: 4px;
}

.board-tag {
    font-size: 13px;
}

/* ------------------------------ 统计卡 ------------------------------ */

.stats {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 10px;
    flex: 0 0 auto;
}

.stat-card {
    display: flex;
    align-items: baseline;
    gap: 6px;
    padding: 12px 16px;
}

.stat-label {
    font-size: 14px;
    color: var(--text-secondary);
}

.stat-value {
    margin-left: auto;
    font-size: 21px;
    font-weight: 600;
    color: var(--text-primary);
}

.stat-unit {
    font-size: 13px;
    color: var(--text-placeholder);
}

/* ------------------------------ 主体 ------------------------------ */

.grid {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 352px;
    gap: 10px;
    flex: 1 1 auto;
    min-height: 0;
}

.table-panel {
    display: flex;
    flex-direction: column;
    min-height: 0;
    overflow: hidden;
}

.panel-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    height: 40px;
    padding: 0 8px 0 4px;
    border-bottom: 1px solid var(--divider);
    flex: 0 0 auto;
}

.tabs {
    display: flex;
    align-items: center;
    gap: 2px;
}

.tab {
    height: 28px;
    padding: 0 12px;
    border: none;
    border-radius: 4px;
    background: transparent;
    font-size: 15px;
    color: var(--text-regular);
    cursor: pointer;
}

.tab:hover {
    background: var(--bg-hover);
}

.tab.active {
    background: var(--primary-weak);
    color: var(--primary);
    font-weight: 600;
}

.need-pwd {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 10px 14px;
    margin: 10px;
    border: 1px solid rgba(245, 165, 36, 0.35);
    background: rgba(245, 165, 36, 0.08);
    border-radius: 4px;
    flex: 0 0 auto;
}

.need-pwd-text {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 14px;
    color: #a8700f;
}

.table-body {
    flex: 1 1 auto;
    min-height: 0;
}

/* ------------------------------ 下单面板 ------------------------------ */

.order-panel {
    display: flex;
    flex-direction: column;
    min-height: 0;
    overflow-y: auto;
}

.order-head {
    justify-content: space-between;
    padding: 0 12px 0 14px;
}

.head-title {
    font-size: 15px;
    font-weight: 600;
}

.stock-line {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    padding: 10px 14px;
    border-bottom: 1px solid var(--divider);
}

.stock-name {
    display: flex;
    align-items: baseline;
    gap: 6px;
    min-width: 0;
}

.nm {
    font-size: 16px;
    font-weight: 600;
    color: var(--text-primary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.cd {
    font-size: 13px;
    color: var(--text-secondary);
}

.stock-quote {
    display: flex;
    align-items: baseline;
    gap: 8px;
}

.qp {
    font-size: 19px;
    font-weight: 700;
}

.pc {
    font-size: 14px;
    font-weight: 600;
}

.dim {
    font-size: 14px;
    color: var(--text-placeholder);
}
</style>
