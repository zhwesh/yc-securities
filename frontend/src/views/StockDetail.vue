<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AuctionPanel from '@/components/AuctionPanel.vue'
import OrderBook from '@/components/OrderBook.vue'
import OrderForm from '@/components/OrderForm.vue'
import OrderTable from '@/components/OrderTable.vue'
import PositionTable from '@/components/PositionTable.vue'
import QuoteHeader from '@/components/QuoteHeader.vue'
import StockChart from '@/components/StockChart.vue'
import StockStats from '@/components/StockStats.vue'
import TradeTable from '@/components/TradeTable.vue'
import TradeTape from '@/components/TradeTape.vue'
import { marketApi, stockApi } from '@/api/market'
import { useAccountStore } from '@/stores/account'
import { useTradeAuthStore } from '@/stores/tradeAuth'
import type { OrderBookVO, StockDetailVO, StockQuoteVO } from '@/types'
import { marketSocket } from '@/ws/marketSocket'

type DialogKind = 'buy' | 'sell' | 'cancel' | 'trade' | null

const route = useRoute()
const router = useRouter()
const account = useAccountStore()
const tradeAuth = useTradeAuthStore()

const code = computed(() => String(route.params.code ?? ''))

const quote = ref<StockQuoteVO | null>(null)
const detail = ref<StockDetailVO | null>(null)
const book = ref<OrderBookVO | null>(null)
const loadingQuote = ref(false)
const loadError = ref('')

const tab = ref('minute')
const adjust = ref(1)
const dialog = ref<DialogKind>(null)
const ordersKey = ref(0)

let offSub: (() => void) | null = null

/* ------------------------------- 图表 tab ------------------------------- */

const chartTabs = [
    { id: 'minute', label: '分时', kind: 'minute' },
    { id: 'dk1', label: '日k', kind: 'dayK' },
    { id: 'dk2', label: '周k', kind: 'dayK' },
    { id: 'dk3', label: '月k', kind: 'dayK' },
    { id: 'dk4', label: '季k', kind: 'dayK' },
    { id: 'dk5', label: '年k', kind: 'dayK' },
    { id: 'mk1', label: '1分', kind: 'minuteK' },
    { id: 'mk5', label: '5分', kind: 'minuteK' },
    { id: 'mk15', label: '15分', kind: 'minuteK' },
    { id: 'mk30', label: '30分', kind: 'minuteK' },
    { id: 'mk60', label: '60分', kind: 'minuteK' },
    { id: 'mk120', label: '120分', kind: 'minuteK' }
]

const isDayK = computed(() => chartTabs.find((t) => t.id === tab.value)?.kind === 'dayK')

/* ------------------------------- 派生数据 ------------------------------- */

const stockName = computed(() => quote.value?.name || detail.value?.name || code.value)

const board = computed(() => {
    if (detail.value?.board) return detail.value.board
    if (/^(300|301)/.test(code.value)) return '创业板'
    if (/^(688|689)/.test(code.value)) return '科创板'
    if (/^(4|8)/.test(code.value)) return '北交所'
    return '主板'
})

const preClose = computed(() => quote.value?.preClose ?? 0)

/** 当前资金账户在这只股票上的持仓（正常只有一条） */
const myPositions = computed(() => account.positions.filter((p) => p.code === code.value))
const myPosition = computed(() => myPositions.value[0] ?? null)

/** 底部面板：该股持仓 / 该股当前委托 */
const bottomTab = ref<'position' | 'orders'>('position')

/* ---------------------- 底部面板高度（可拖拽） ---------------------- */

const BOTTOM_MIN = 44
const BOTTOM_DEFAULT = 196
const BOTTOM_LS_KEY = 'yc:stock-bottom-h'

const detailEl = ref<HTMLElement | null>(null)
const resizing = ref(false)

function readBottomH(): number {
    try {
        const n = Number(localStorage.getItem(BOTTOM_LS_KEY))
        if (Number.isFinite(n) && n >= BOTTOM_MIN) return n
    } catch {
        /* ignore */
    }
    return BOTTOM_DEFAULT
}

function persistBottomH(): void {
    try {
        localStorage.setItem(BOTTOM_LS_KEY, String(Math.round(bottomH.value)))
    } catch {
        /* ignore */
    }
}

const bottomH = ref(readBottomH())

/** 拖动分隔条调整高度：往上拖变高，往下拖变矮，最低只剩标题条 */
function onResizeStart(e: MouseEvent): void {
    e.preventDefault()
    const startY = e.clientY
    const startH = bottomH.value
    const maxH = Math.max(BOTTOM_MIN + 80, (detailEl.value?.clientHeight ?? 800) * 0.58)
    resizing.value = true

    const onMove = (ev: MouseEvent) => {
        const next = startH - (ev.clientY - startY)
        bottomH.value = Math.min(maxH, Math.max(BOTTOM_MIN, next))
    }
    const onUp = () => {
        resizing.value = false
        window.removeEventListener('mousemove', onMove)
        window.removeEventListener('mouseup', onUp)
        persistBottomH()
    }
    window.addEventListener('mousemove', onMove)
    window.addEventListener('mouseup', onUp)
}

/** 双击分隔条：在默认高度 / 收起之间切换 */
function toggleBottom(): void {
    bottomH.value = bottomH.value <= BOTTOM_MIN + 6 ? BOTTOM_DEFAULT : BOTTOM_MIN
    persistBottomH()
}

// 记住的高度可能来自更大的窗口，挂载后按当前可视高度收一下，别把图表挤没
let colRo: ResizeObserver | null = null

onMounted(() => {
    const maxH = Math.max(BOTTOM_MIN + 80, (detailEl.value?.clientHeight ?? 800) * 0.58)
    if (bottomH.value > maxH) {
        bottomH.value = Math.round(maxH)
        persistBottomH()
    }

    // 右栏高度变了（比如拖了底部面板 / 改窗口大小）就把盘口高度收进合法区间，
    // 保证明细区域最小高度
    const clampBook = () => {
        const max = bookMaxH()
        if (bookH.value > max) {
            bookH.value = max
            persistBookH()
        }
    }
    clampBook()
    if (colRightEl.value && typeof ResizeObserver !== 'undefined') {
        colRo = new ResizeObserver(clampBook)
        colRo.observe(colRightEl.value)
    }
})

onBeforeUnmount(() => {
    colRo?.disconnect()
    colRo = null
})

/* ---------------- 右栏：五档盘口 / 明细 的可调分配 ---------------- */

const BOOK_MIN = 150
const BOOK_DEFAULT = 300
const BOOK_LS_KEY = 'yc:stock-book-h'

const colRightEl = ref<HTMLElement | null>(null)
const resizingBook = ref(false)

function readBookH(): number {
    try {
        const n = Number(localStorage.getItem(BOOK_LS_KEY))
        if (Number.isFinite(n) && n >= BOOK_MIN) return n
    } catch {
        /* ignore */
    }
    return BOOK_DEFAULT
}

const bookH = ref(readBookH())

function persistBookH(): void {
    try {
        localStorage.setItem(BOOK_LS_KEY, String(Math.round(bookH.value)))
    } catch {
        /* ignore */
    }
}

/** 盘口最高占满整栏，但要给明细留至少130px */
function bookMaxH(): number {
    const colH = colRightEl.value?.clientHeight ?? 600
    return Math.max(BOOK_MIN, colH - 138)
}

function onBookResizeStart(e: MouseEvent): void {
    e.preventDefault()
    const startY = e.clientY
    const startH = bookH.value
    const maxH = bookMaxH()
    resizingBook.value = true

    const onMove = (ev: MouseEvent) => {
        const next = startH + (ev.clientY - startY)
        bookH.value = Math.min(maxH, Math.max(BOOK_MIN, next))
    }
    const onUp = () => {
        resizingBook.value = false
        window.removeEventListener('mousemove', onMove)
        window.removeEventListener('mouseup', onUp)
        persistBookH()
    }
    window.addEventListener('mousemove', onMove)
    window.addEventListener('mouseup', onUp)
}

/* ------------------------------- 数据加载 ------------------------------- */

async function loadQuote(): Promise<void> {
    if (!code.value) return
    loadingQuote.value = true
    loadError.value = ''
    try {
        const [q, d] = await Promise.all([
            marketApi.quote(code.value),
            stockApi.detail(code.value).catch(() => null)
        ])
        quote.value = q
        detail.value = d
    } catch (e) {
        loadError.value = e instanceof Error ? e.message : '行情加载失败'
        quote.value = null
    } finally {
        loadingQuote.value = false
    }
}

async function loadBook(): Promise<void> {
    if (!code.value) return
    try {
        book.value = await marketApi.orderBook(code.value, 5)
    } catch {
        book.value = null
    }
}

function subscribe(): void {
    offSub?.()
    offSub = null
    if (!code.value) return
    offSub = marketSocket.subscribe([code.value], {
        quote: (q) => {
            quote.value = { ...(quote.value ?? ({} as StockQuoteVO)), ...q }
        },
        orderBook: (c, b) => {
            if (c === code.value) book.value = b
        }
    })
}

async function loadAll(): Promise<void> {
    await loadQuote()
    await loadBook()
    subscribe()
    if (account.current && !account.positions.length) {
        void account.loadPositions()
    }
}

/* ------------------------------- 交互 ------------------------------- */

function openDialog(kind: 'buy' | 'sell' | 'cancel' | 'detail'): void {
    if (!account.current) {
        ElMessage.warning('请先开通资金账户')
        void router.push('/assets')
        return
    }
    // 设计图右上的「明细」= 该股的成交记录
    dialog.value = kind === 'detail' ? 'trade' : kind
}

function closeDialog(): void {
    dialog.value = null
}

async function onSubmitted(): Promise<void> {
    closeDialog()
    await account.refreshAccount()
    if (account.currentId && tradeAuth.readOnline(account.currentId)) {
        await account.loadPositions()
    }
    ordersKey.value += 1
}

async function verifyTradePwd(): Promise<void> {
    const id = account.currentId
    if (!id) return
    const ok = await tradeAuth.prompt(id)
    if (ok) {
        await account.loadPositions()
        ElMessage.success('交易密码已验证')
    }
}

/* ------------------------------- 生命周期 ------------------------------- */

onMounted(loadAll)

watch(code, () => {
    tab.value = 'minute'
    void loadAll()
})

onBeforeUnmount(() => {
    offSub?.()
    offSub = null
})
</script>

<template>
    <div
        ref="detailEl"
        v-loading="loadingQuote && !quote"
        class="detail"
        :class="{ 'is-resizing': resizing || resizingBook }"
    >
        <QuoteHeader :quote="quote" :detail="detail" @action="openDialog" />

        <!-- 图表 tab -->
        <div class="tab-bar">
            <div class="tabs">
                <button
                    v-for="t in chartTabs"
                    :key="t.id"
                    class="tab"
                    :class="{ active: tab === t.id }"
                    @click="tab = t.id"
                >
                    {{ t.label }}
                </button>
            </div>

            <div class="tab-right">
                <el-radio-group v-if="isDayK" v-model="adjust" size="small">
                    <el-radio-button :value="0">不复权</el-radio-button>
                    <el-radio-button :value="1">前复权</el-radio-button>
                    <el-radio-button :value="2">后复权</el-radio-button>
                </el-radio-group>
                <span v-else class="tab-hint">分钟线不支持复权</span>
            </div>
        </div>

        <!-- 三栏主体 -->
        <div class="body">
            <!-- 左：集合竞价 -->
            <aside class="col-left">
                <section class="panel auction-panel">
                    <div class="panel-head">集合竞价</div>
                    <AuctionPanel :code="code" />
                </section>
            </aside>

            <!-- 中：主图 + 副图 + 统计量 -->
            <section class="col-center">
                <div class="panel chart-panel">
                    <StockChart :code="code" :tab="tab" :adjust="adjust" />
                </div>
                <StockStats :quote="quote" :detail="detail" />
            </section>

            <!-- 右：五档盘口 + 明细（两者之间可拖动调整） -->
            <aside ref="colRightEl" class="col-right">
                <section class="panel book-panel" :style="{ height: bookH + 'px' }">
                    <div class="panel-head">
                        五档盘口
                        <span class="head-extra">{{ stockName }}</span>
                    </div>
                    <OrderBook :book="book" :pre-close="preClose" :price="quote?.price ?? 0" dense />
                    <div v-if="account.needTradeAuth" class="pwd-tip">
                        <span>持仓查询需验证交易密码</span>
                        <el-button size="small" text type="primary" @click="verifyTradePwd">验证</el-button>
                    </div>
                </section>

                <div
                    class="splitter"
                    :class="{ dragging: resizingBook }"
                    title="拖动调整盘口 / 明细的高度"
                    @mousedown="onBookResizeStart"
                ></div>

                <section class="panel tape-panel">
                    <div class="panel-head">
                        明细
                        <span class="head-extra">最近成交</span>
                    </div>
                    <TradeTape :code="code" :pre-close="preClose" :limit="50" />
                </section>
            </aside>
        </div>

        <!-- 底部：该股持仓 / 该股当前委托（高度可拖拽） -->
        <div
            class="splitter"
            :class="{ dragging: resizing }"
            title="拖动调整高度，双击收起/展开"
            @mousedown="onResizeStart"
            @dblclick="toggleBottom"
        ></div>

        <section class="panel hold-panel" :style="{ height: bottomH + 'px' }">
            <div class="panel-head hold-head">
                <div class="tabs">
                    <button
                        class="tab"
                        :class="{ active: bottomTab === 'position' }"
                        @click="bottomTab = 'position'"
                    >
                        当前持仓
                        <span v-if="myPositions.length" class="badge">{{ myPositions.length }}</span>
                    </button>
                    <button
                        class="tab"
                        :class="{ active: bottomTab === 'orders' }"
                        @click="bottomTab = 'orders'"
                    >
                        当前委托
                    </button>
                </div>

                <div class="hold-actions">
                    <span v-if="account.needTradeAuth && bottomTab === 'position'" class="hold-hint">
                        查询持仓需验证交易密码
                    </span>
                    <el-button
                        v-if="account.needTradeAuth && bottomTab === 'position'"
                        size="small"
                        text
                        type="primary"
                        @click="verifyTradePwd"
                    >
                        验证
                    </el-button>
                    <el-button size="small" text @click="account.loadPositions()">
                        <el-icon><Refresh /></el-icon>
                    </el-button>
                </div>
            </div>

            <div class="hold-body">
                <PositionTable
                    v-if="bottomTab === 'position'"
                    :positions="myPositions"
                    :loading="account.positionsLoading"
                    compact
                />
                <OrderTable
                    v-else
                    :key="`hold-orders-${ordersKey}`"
                    :account-id="account.currentId"
                    :code="code"
                    :auction-code="code"
                    :page-size="20"
                />
            </div>
        </section>

        <div v-if="loadError" class="load-error">{{ loadError }}</div>

        <!-- 买入 -->
        <el-dialog
            :model-value="dialog === 'buy'"
            title="买入"
            width="392px"
            append-to-body
            :close-on-click-modal="false"
            @update:model-value="(v: boolean) => !v && closeDialog()"
        >
            <OrderForm
                v-if="dialog === 'buy' && account.current"
                :key="`buy-${code}`"
                :code="code"
                :stock-name="stockName"
                :quote="quote"
                :board="board"
                :position="myPosition"
                default-side="buy"
                @submitted="onSubmitted"
            />
        </el-dialog>

        <!-- 卖出 -->
        <el-dialog
            :model-value="dialog === 'sell'"
            title="卖出"
            width="392px"
            append-to-body
            :close-on-click-modal="false"
            @update:model-value="(v: boolean) => !v && closeDialog()"
        >
            <OrderForm
                v-if="dialog === 'sell' && account.current"
                :key="`sell-${code}`"
                :code="code"
                :stock-name="stockName"
                :quote="quote"
                :board="board"
                :position="myPosition"
                default-side="sell"
                @submitted="onSubmitted"
            />
        </el-dialog>

        <!-- 撤单 -->
        <el-dialog
            :model-value="dialog === 'cancel'"
            title="当前委托（可撤单）"
            width="900px"
            append-to-body
            @update:model-value="(v: boolean) => !v && closeDialog()"
        >
            <div class="dialog-table">
                <OrderTable
                    v-if="dialog === 'cancel'"
                    :key="`cancel-${ordersKey}`"
                    :account-id="account.currentId"
                    :code="code"
                    :page-size="20"
                />
            </div>
        </el-dialog>

        <!-- 明细（该股成交记录） -->
        <el-dialog
            :model-value="dialog === 'trade'"
            title="成交明细"
            width="820px"
            append-to-body
            @update:model-value="(v: boolean) => !v && closeDialog()"
        >
            <div class="dialog-table">
                <TradeTable
                    v-if="dialog === 'trade'"
                    :key="`trade-${ordersKey}`"
                    :account-id="account.currentId"
                    :code="code"
                    :page-size="20"
                />
            </div>
        </el-dialog>
    </div>
</template>

<style scoped>
.detail {
    display: flex;
    flex-direction: column;
    gap: 8px;
    height: 100%;
    min-height: 0;
}

/* ------------------------------ tab 行 ------------------------------ */

.tab-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding: 0 8px;
    flex: 0 0 auto;
}

.tabs {
    display: flex;
    align-items: center;
    gap: 4px;
}

.tab {
    position: relative;
    height: 30px;
    padding: 0 14px;
    border: none;
    background: transparent;
    font-size: 15px;
    color: var(--text-regular);
    cursor: pointer;
    transition: color 0.15s;
}

.tab:hover {
    color: var(--primary);
}

.tab.active {
    color: var(--primary);
    font-weight: 600;
}

.tab.active::after {
    content: '';
    position: absolute;
    left: 12px;
    right: 12px;
    bottom: 0;
    height: 2px;
    border-radius: 2px;
    background: var(--primary);
}

.tab-right {
    display: flex;
    align-items: center;
    gap: 8px;
}

.tab-hint {
    font-size: 13px;
    color: var(--text-placeholder);
}

/* ------------------------------ 三栏 ------------------------------ */

.body {
    display: grid;
    grid-template-columns: 170px minmax(0, 1fr) 238px;
    /* 单行高度约束在可用空间内 */
    grid-template-rows: minmax(0, 1fr);
    gap: 8px;
    flex: 1 1 auto;
    min-height: 0;
}

.col-left,
.col-right {
    display: flex;
    flex-direction: column;
    gap: 8px;
    min-height: 0;
}

/* 右栏自己不再滚动：盘口固定高、明细吃掉剩余高度并在内部滚动 */
.col-right {
    overflow: hidden;
}

.col-center {
    display: flex;
    flex-direction: column;
    gap: 8px;
    min-height: 0;
    min-width: 0;
}

.panel-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 30px;
    padding: 0 10px;
    border-bottom: 1px solid var(--divider);
    font-size: 14px;
    font-weight: 600;
    color: var(--text-primary);
    flex: 0 0 auto;
}

.head-extra {
    font-size: 12px;
    font-weight: 400;
    color: var(--text-placeholder);
}

.auction-panel {
    display: flex;
    flex-direction: column;
    overflow: hidden;
}

.chart-panel {
    flex: 1 1 auto;
    min-height: 0;
    overflow: hidden;
}

.book-panel {
    display: flex;
    flex-direction: column;
    flex: 0 0 auto;
    overflow: hidden;
}

.pwd-tip {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 6px;
    padding: 4px 10px;
    font-size: 12px;
    color: var(--text-secondary);
    background: var(--bg-subtle);
}

.tape-panel {
    display: flex;
    flex-direction: column;
    flex: 1 1 auto;
    /* 不设最小高度：盘口高度由拖拽决定，剩余空间全给明细，自己内部滚动 */
    min-height: 0;
    overflow: hidden;
}

.tape-panel :deep(.tape) {
    flex: 1 1 auto;
    min-height: 0;
    overflow: hidden;
}

/* ---------------- 底部：该股持仓 / 当前委托 ---------------- */

.hold-panel {
    display: flex;
    flex-direction: column;
    flex: 0 0 auto;
    min-height: 0;
    overflow: hidden;
}

/* 拖拽分隔条：视觉上很细，但可点区域高一倍 */
.splitter {
    flex: 0 0 8px;
    margin: -3px 0;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: row-resize;
    position: relative;
    z-index: 2;
}

.splitter::after {
    content: '';
    width: 44px;
    height: 3px;
    border-radius: 2px;
    background: var(--border-strong);
    transition: background 0.15s, width 0.15s;
}

.splitter:hover::after,
.splitter.dragging::after {
    width: 76px;
    background: var(--primary);
}

/* 拖动期间禁用文本选择 */
.detail.is-resizing {
    user-select: none;
    cursor: row-resize;
}

.hold-head {
    height: 38px;
}

.hold-actions {
    display: flex;
    align-items: center;
    gap: 4px;
}

.hold-hint {
    font-size: 13px;
    color: var(--text-secondary);
}

.hold-body {
    flex: 1 1 auto;
    min-height: 0;
}

.load-error {
    font-size: 14px;
    color: var(--up);
    text-align: center;
}

.dialog-table {
    height: 420px;
}
</style>
