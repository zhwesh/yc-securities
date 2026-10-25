<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { orderApi } from '@/api/order'
import { ApiError } from '@/api/http'
import { useAccountStore } from '@/stores/account'
import { useTradeAuthStore } from '@/stores/tradeAuth'
import { useAuctionPhase } from '@/composables/useAuctionPhase'
import type { OrderVO, PositionVO, StockQuoteVO } from '@/types'
import { fmtMoney, fmtPrice, limitPercent } from '@/utils/format'
import { FEE_RATE, LOT_SIZE, estimateFee, nextOrderKey, round2 } from '@/utils/trade'

const props = defineProps<{
    code: string
    stockName: string
    quote: StockQuoteVO | null
    board: string
    position: PositionVO | null
    /** 外部指定默认买卖方向（例如从持仓点「卖出」进来） */
    defaultSide?: 'buy' | 'sell'
}>()

const emit = defineEmits<{
    (e: 'submitted', order: OrderVO): void
}>()

const account = useAccountStore()
const tradeAuth = useTradeAuthStore()

/**
 * 验证交易密码并重新加载持仓
 */
async function verifyTradePwd(): Promise<void> {
    const id = account.current?.id
    if (!id) return
    const ok = await tradeAuth.ensure(id)
    if (ok) await account.loadPositions()
}

/** 轮询集合竞价阶段 */
const {
    isAuction: auctionActive,
    phaseName: auctionPhaseName,
    limitOnly: auctionLimitOnly
} = useAuctionPhase(() => props.code)

// 初始方向必须直接取 prop：弹框每次都是新挂载的组件，
// 如果只在 watch 里赋值，prop 在创建时就已经是终值、watch 永远不会触发，
// 结果点「卖出」弹出的还是买入面板
const side = ref<'buy' | 'sell'>(props.defaultSide ?? 'buy')
const orderType = ref<1 | 2>(1)
const form = reactive({
    price: 0,
    lots: 1
})
const submitting = ref(false)
const errorMsg = ref('')
let pendingOrderKey: string | null = null

watch(
    [() => props.code, () => account.current?.id, side, orderType, () => form.price, () => form.lots],
    () => {
        pendingOrderKey = null
    }
)

/** 涨跌停幅度（后端按 stock.board 判断） */
const limitPct = computed(() => limitPercent(props.board, props.code))

/**
 * 可委托区间：以**昨收**为基准算涨跌停价。
 * A 股涨跌停价 = 前收盘价 × (1 ± 幅度)，四舍五入到分。
 * 后端 OrderServiceImpl 同样是 `priceBase = quote.preClose ?? quote.price`，
 * 只有拿不到昨收时才退回现价。
*/
const priceBase = computed(() => props.quote?.preClose || props.quote?.price || 0)

const priceRange = computed(() => {
    const base = priceBase.value
    if (!base) return { upper: 0, lower: 0 }
    return {
        upper: round2(base * (1 + limitPct.value / 100)),
        lower: round2(base * (1 - limitPct.value / 100))
    }
})

const shares = computed(() => form.lots * LOT_SIZE)

/** 限价用填的价格，市价用当前价估算 */
const effPrice = computed(() => {
    if (orderType.value === 1) return form.price
    // 市价单按涨停价估算冻结资金
    return priceRange.value.upper || props.quote?.price || 0
})

const amount = computed(() => round2(effPrice.value * shares.value))
const fee = computed(() => estimateFee(amount.value, side.value))
const totalCost = computed(() => round2(amount.value + fee.value))
const proceeds = computed(() => round2(amount.value - fee.value))

const available = computed(() => account.current?.availableAssets ?? 0)
const sellable = computed(() => props.position?.availableQuantity ?? 0)
const holding = computed(() => props.position?.quantity ?? 0)

/** 可买手数：可用资金 / (价格 × 100 × (1 + 买入费率)) */
const maxBuyLots = computed(() => {
    const p = effPrice.value
    if (!p || p <= 0) return 0
    const perLot = p * LOT_SIZE * (1 + FEE_RATE.commission + FEE_RATE.transfer)
    return Math.max(0, Math.floor(available.value / perLot))
})

const maxSellLots = computed(() => Math.floor(sellable.value / LOT_SIZE))

// 数量输入上限，单位手
const maxLots = computed(() => (side.value === 'buy' ? maxBuyLots.value : maxSellLots.value))

const needTradePwdHint = computed(
    () =>
        (side.value === 'buy' && available.value <= 0) ||
        (side.value === 'sell' && holding.value > 0 && sellable.value === 0)
)

// 外部切换方向（例如在持仓里点「卖出」）时同步过来
watch(
    () => props.defaultSide,
    (v) => {
        if (v) side.value = v
    },
    { immediate: true }
)

/** 初始化价格：跟随当前价 */
watch(
    () => [props.quote?.price, side.value] as const,
    () => {
        const p = props.quote?.price
        if (p && p > 0 && (form.price === 0 || form.price === props.quote?.preClose)) {
            form.price = Number(p.toFixed(2))
        }
    },
    { immediate: true }
)

watch(
    () => props.code,
    () => {
        form.lots = 1
        form.price = props.quote?.price ? Number(props.quote.price.toFixed(2)) : 0
        errorMsg.value = ''
    }
)

function toBuy(): void {
    side.value = 'buy'
    errorMsg.value = ''
}

function toSell(): void {
    side.value = 'sell'
    errorMsg.value = ''
}

function setType(t: 1 | 2): void {
    orderType.value = t
    errorMsg.value = ''
    if (t === 1 && props.quote?.price) {
        form.price = Number(props.quote.price.toFixed(2))
    }
}

function quickLots(ratio: number): void {
    const max = maxLots.value
    const v = Math.floor(max * ratio)
    form.lots = Math.max(v, max > 0 ? 1 : 0)
}

function stepPrice(delta: number): void {
    const base = form.price || props.quote?.price || 0
    form.price = round2(Math.max(0.01, base + delta))
}

/** 提交前本地校验，尽量把后端会报的错提前拦住 */
function validate(): string | null {
    if (!account.current) return '请先开通资金账户'
    if (account.current.status === 2) return '资金账户已被冻结'
    if (props.board && props.board !== '主板' && !account.current.boards.includes(props.board)) {
        return `账户未开通${props.board}交易权限`
    }
    // 竞价阶段只接受限价单
    if (orderType.value === 2 && auctionLimitOnly.value) {
        return `集合竞价阶段仅支持限价委托（当前：${auctionPhaseName.value || '集合竞价'}）`
    }
    if (!shares.value || shares.value <= 0) return '请输入委托数量'
    if (shares.value % LOT_SIZE !== 0) return '委托数量必须是100股的整数倍'
    if (orderType.value === 1) {
        if (!form.price || form.price <= 0) return '限价单价格必须大于0'
        if (priceRange.value.upper > 0) {
            if (form.price > priceRange.value.upper + 1e-9) {
                return `价格超出涨幅限制（最高 ${fmtPrice(priceRange.value.upper)}）`
            }
            if (form.price < priceRange.value.lower - 1e-9) {
                return `价格超出跌幅限制（最低 ${fmtPrice(priceRange.value.lower)}）`
            }
        }
    }
    if (side.value === 'buy' && totalCost.value > available.value + 1e-9) {
        return `可用资金不足，需要 ${fmtMoney(totalCost.value)} 元`
    }
    if (side.value === 'sell') {
        // 持仓要先验证交易密码才能查到，没验证时 sellable 是0，
        // 直接报「可卖数量不足」会让人以为是自己持仓不够
        if (account.needTradeAuth && sellable.value === 0) {
            return '查询持仓需要先验证交易密码'
        }
        if (shares.value > sellable.value) {
            return holding.value > 0 && sellable.value < holding.value
                ? `可卖数量不足（T+1可卖 ${sellable.value} 股，持仓 ${holding.value} 股）`
                : '可卖数量不足'
        }
    }
    return null
}

async function submit(): Promise<void> {
    if (submitting.value) return
    const err = validate()
    if (err) {
        errorMsg.value = err
        ElMessage.warning(err)
        return
    }
    errorMsg.value = ''

    const acc = account.current!
    const sideText = side.value === 'buy' ? '买入' : '卖出'
    const typeText = orderType.value === 1 ? '限价' : '市价'
    const priceText = orderType.value === 1 ? fmtPrice(form.price) : '市价'

    try {
        await ElMessageBox.confirm(
            `确认${sideText} ${props.stockName}（${props.code}）\n` +
                `委托方式：${typeText}　价格：${priceText}\n` +
                `数量：${shares.value} 股（${form.lots} 手）\n` +
                `预估金额：${fmtMoney(amount.value)} 元　预估费用：${fmtMoney(fee.value)} 元\n` +
                (side.value === 'buy'
                    ? `预估扣款：${fmtMoney(totalCost.value)} 元`
                    : `预估到账：${fmtMoney(proceeds.value)} 元`),
            '确认委托',
            {
                confirmButtonText: '确认下单',
                cancelButtonText: '取消',
                type: 'warning',
                customStyle: { whiteSpace: 'pre-line' }
            }
        )
    } catch {
        return
    }

    submitting.value = true
    const orderKey = pendingOrderKey ?? nextOrderKey()
    pendingOrderKey = orderKey
    try {
        const order = await orderApi.create({
            accountId: acc.id,
            code: props.code,
            type: orderType.value,
            // 市价单 price 传 null，后端会忽略
            price: orderType.value === 1 ? form.price : null,
            quantity: side.value === 'buy' ? shares.value : -shares.value,
            orderKey
        })
        pendingOrderKey = null
        ElMessage.success(`委托已提交（${order.status === 3 ? '已全部成交' : '待撮合'}）`)
        emit('submitted', order)
    } catch (e) {
        const msg = e instanceof ApiError ? e.message : '下单失败'
        errorMsg.value = msg
        ElMessage.error(msg)
    } finally {
        submitting.value = false
    }
}
</script>

<template>
    <div class="order-form">
        <div class="side-tabs">
            <button class="side-tab buy" :class="{ active: side === 'buy' }" @click="toBuy">买入</button>
            <button class="side-tab sell" :class="{ active: side === 'sell' }" @click="toSell">卖出</button>
        </div>

        <div class="type-row">
            <button class="type-tab" :class="{ active: orderType === 1 }" @click="setType(1)">限价</button>
            <button
                class="type-tab"
                :class="{ active: orderType === 2 }"
                :disabled="auctionActive"
                :title="auctionActive ? '集合竞价阶段仅支持限价委托' : ''"
                @click="setType(2)"
            >
                市价
            </button>
        </div>

        <div v-if="auctionActive" class="auction-tip">
            <el-icon><Clock /></el-icon>
            {{ auctionPhaseName || '集合竞价' }} · 仅支持限价委托
        </div>

        <div class="field">
            <label>委托价格</label>
            <div class="price-input">
                <el-button size="small" class="step-btn" :disabled="orderType === 2" @click="stepPrice(-0.01)">
                    −
                </el-button>
                <el-input-number
                    v-model="form.price"
                    class="num-input"
                    :min="0.01"
                    :max="priceRange.upper || undefined"
                    :step="0.01"
                    :precision="2"
                    :controls="false"
                    :disabled="orderType === 2"
                    size="small"
                />
                <el-button size="small" class="step-btn" :disabled="orderType === 2" @click="stepPrice(0.01)">
                    +
                </el-button>
            </div>
            <div class="hint-line">
                <span v-if="orderType === 2">
                    市价单以最优价格成交，无需填写价格；下单时按涨停价冻结资金，成交后多退少补
                </span>
                <span v-else-if="priceRange.upper > 0">
                    可委托区间{{ fmtPrice(priceRange.lower) }} ~ {{ fmtPrice(priceRange.upper) }}
                    <span class="dim">（昨收{{ fmtPrice(priceBase) }} ±{{ limitPct }}%）</span>
                </span>
            </div>
        </div>

        <div class="field">
            <label>委托数量</label>
            <div class="lots-input">
                <el-input-number
                    v-model="form.lots"
                    class="num-input"
                    :min="0"
                    :max="maxLots"
                    :step="1"
                    :precision="0"
                    :controls="false"
                    size="small"
                />
                <span class="unit">手</span>
                <span class="shares num">= {{ shares }}股</span>
            </div>
            <div class="quick-row">
                <button class="quick" @click="quickLots(0.25)">1/4</button>
                <button class="quick" @click="quickLots(1 / 3)">1/3</button>
                <button class="quick" @click="quickLots(0.5)">1/2</button>
                <button class="quick" @click="quickLots(1)">全部</button>
            </div>
        </div>

        <div class="summary">
            <div class="sum-row">
                <span>可用资金</span>
                <span class="num">{{ fmtMoney(available) }}</span>
            </div>
            <div v-if="side === 'buy'" class="sum-row">
                <span>可买</span>
                <span class="num">{{ maxBuyLots }}手</span>
            </div>
            <template v-else>
                <div class="sum-row">
                    <span>可卖</span>
                    <span class="num">{{ sellable }}股 / {{ maxSellLots }}手</span>
                </div>
                <div class="sum-row dim-row">
                    <span>持仓</span>
                    <span class="num">{{ holding }}股</span>
                </div>
                <div v-if="account.needTradeAuth" class="sum-row dim-row">
                    <span>持仓未加载</span>
                    <el-button text size="small" type="primary" @click="verifyTradePwd">
                        验证交易密码
                    </el-button>
                </div>
            </template>

            <div class="sum-row">
                <span>预估金额</span>
                <span class="num">{{ fmtMoney(amount) }}</span>
            </div>
            <div class="sum-row">
                <span>预估费用</span>
                <span class="num">{{ fmtMoney(fee) }}</span>
            </div>
            <div class="sum-row total">
                <span>{{ side === 'buy' ? '预估扣款' : '预估到账' }}</span>
                <span class="num" :class="side === 'buy' ? 'up' : 'down'">
                    {{ fmtMoney(side === 'buy' ? totalCost : proceeds) }}
                </span>
            </div>
        </div>

        <div v-if="needTradePwdHint" class="t1-hint">
            <el-icon><WarningFilled /></el-icon>
            {{ side === 'sell' ? '当日买入的股票 T+1后才可卖出' : '可用资金不足，请先入金' }}
        </div>

        <div v-if="errorMsg" class="err">{{ errorMsg }}</div>

        <el-button
            class="submit-btn"
            :class="side === 'buy' ? 'is-buy' : 'is-sell'"
            :loading="submitting"
            @click="submit"
        >
            {{ side === 'buy' ? '买入' : '卖出' }} {{ stockName }}
        </el-button>

        <div class="foot-note">
            费率：佣金0.025%（双边）· 印花税0.05%（仅卖出）· 过户费0.001%（双边）
        </div>
    </div>
</template>

<style scoped>
.order-form {
    display: flex;
    flex-direction: column;
    gap: 10px;
    padding: 12px;
}

.side-tabs {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 6px;
}

.side-tab {
    height: 32px;
    border: 1px solid var(--border-strong);
    border-radius: 4px;
    background: var(--bg-panel);
    font-size: 15px;
    font-weight: 600;
    color: var(--text-regular);
    cursor: pointer;
    transition: all 0.15s;
}

.side-tab.buy.active {
    background: var(--up);
    border-color: var(--up);
    color: #fff;
}

.side-tab.sell.active {
    background: var(--down);
    border-color: var(--down);
    color: #fff;
}

.type-row {
    display: flex;
    gap: 6px;
}

.type-tab {
    height: 26px;
    padding: 0 12px;
    border: 1px solid var(--border);
    border-radius: 4px;
    background: var(--bg-panel);
    font-size: 14px;
    color: var(--text-secondary);
    cursor: pointer;
}

.type-tab.active {
    border-color: var(--primary);
    color: var(--primary);
    background: var(--primary-weak);
}

.field {
    display: flex;
    flex-direction: column;
    gap: 5px;
}

.field > label {
    font-size: 14px;
    color: var(--text-secondary);
}

.price-input {
    display: flex;
    gap: 6px;
    align-items: center;
}

.price-input :deep(.el-input-number) {
    flex: 1 1 auto;
}

.step-btn {
    flex: 0 0 30px;
    padding: 0;
    font-size: 17px;
    line-height: 1;
}

.lots-input {
    display: flex;
    align-items: center;
    gap: 6px;
}

.lots-input :deep(.el-input-number) {
    flex: 0 0 96px;
}

.unit {
    font-size: 14px;
    color: var(--text-secondary);
}

.shares {
    margin-left: auto;
    font-size: 14px;
    color: var(--text-regular);
}

.hint-line {
    font-size: 13px;
    color: var(--text-placeholder);
    min-height: 16px;
}

.dim {
    color: var(--text-placeholder);
}

.quick-row {
    display: flex;
    gap: 6px;
}

.quick {
    flex: 1 1 0;
    height: 24px;
    border: 1px solid var(--border);
    border-radius: 3px;
    background: var(--bg-subtle);
    font-size: 13px;
    color: var(--text-regular);
    cursor: pointer;
}

.quick:hover {
    border-color: var(--primary);
    color: var(--primary);
}

.summary {
    padding: 8px 10px;
    border-radius: 4px;
    background: var(--bg-subtle);
    display: flex;
    flex-direction: column;
    gap: 4px;
}

.sum-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: 14px;
    color: var(--text-secondary);
}

.sum-row .num {
    color: var(--text-primary);
}

.dim-row .num {
    color: var(--text-secondary);
}

.sum-row.total {
    margin-top: 4px;
    padding-top: 6px;
    border-top: 1px dashed var(--border-strong);
    font-weight: 600;
}

.sum-row.total .num {
    font-size: 16px;
    font-weight: 700;
}

.auction-tip {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 6px 10px;
    border-radius: 4px;
    background: rgba(245, 165, 36, 0.1);
    border: 1px solid rgba(245, 165, 36, 0.35);
    font-size: 13px;
    color: #a8700f;
    line-height: 1.5;
}

.t1-hint {
    display: flex;
    align-items: center;
    gap: 5px;
    font-size: 13px;
    color: var(--el-color-warning, #f5a524);
    line-height: 1.5;
}

.err {
    font-size: 14px;
    color: var(--up);
    line-height: 1.5;
}

.submit-btn {
    width: 100%;
    height: 36px;
    font-size: 16px;
    font-weight: 600;
    color: #fff;
    border: none;
}

.submit-btn.is-buy {
    background: var(--up);
}

.submit-btn.is-buy:hover {
    background: var(--primary-hover);
}

.submit-btn.is-sell {
    background: var(--down);
}

.submit-btn.is-sell:hover {
    background: #009463;
}

.foot-note {
    font-size: 12px;
    line-height: 1.5;
    color: var(--text-placeholder);
}
</style>
