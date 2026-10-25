<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { orderApi } from '@/api/order'
import { ApiError } from '@/api/http'
import type { OrderVO } from '@/types'
import { ORDER_STATUS_TEXT } from '@/types'
import { fmtDateTime, fmtPrice } from '@/utils/format'
import { useAuctionPhase } from '@/composables/useAuctionPhase'
import { marketSocket } from '@/ws/marketSocket'

const props = defineProps<{
    accountId: number | null
    // 按股票代码过滤
    code?: string
    // 按状态过滤
    status?: number
    pageSize?: number
    // 显示分页器
    showPager?: boolean
    // 行可点击
    clickable?: boolean
    // 判断竞价时段的股票代码，省略时使用code
    auctionCode?: string
}>()

const emit = defineEmits<{
    (e: 'open', order: OrderVO): void
}>()

const { cancelAllowed, cancelBlockReason } = useAuctionPhase(
    () => props.auctionCode ?? props.code
)

// 当前页记录
const rows = ref<OrderVO[]>([])
// 记录总数
const total = ref(0)
// 当前页码
const pageNum = ref(1)
// 加载状态
const loading = ref(false)
// 错误信息
const error = ref('')
// 正在撤单的委托标识
const cancellingId = ref<number | null>(null)

// 每页条数
const size = computed(() => props.pageSize ?? 20)

// 取消委托更新监听函数
let offUpdate: (() => void) | null = null
// 刷新定时器句柄
let refreshTimer: number | null = null

/**
 * 加载委托列表
 */
async function load(): Promise<void> {
    const id = props.accountId
    if (!id) {
        rows.value = []
        total.value = 0
        return
    }
    loading.value = true
    error.value = ''
    try {
        const page = await orderApi.list({
            accountId: id,
            code: props.code || undefined,
            status: props.status,
            pageNum: pageNum.value,
            pageSize: size.value
        })
        rows.value = page.records
        total.value = page.total
    } catch (e) {
        error.value = e instanceof ApiError ? e.message : '委托列表加载失败'
        rows.value = []
        total.value = 0
    } finally {
        loading.value = false
    }
}

/**
 * 重新加载委托列表
 */
function reload(): void {
    void load()
}

/**
 * 在合并窗口内安排一次刷新
 */
function scheduleRefresh(): void {
    if (refreshTimer !== null) return
    refreshTimer = window.setTimeout(() => {
        refreshTimer = null
        void load()
    }, 400)
}

/**
 * 判断委托是否可撤销
 * @param row 委托信息
 * @returns 未成交或部分成交时返回true
 */
function canCancel(row: OrderVO): boolean {
    return row.status === 1 || row.status === 2
}

/**
 * 撤销委托
 * @param row 委托信息
 */
async function cancel(row: OrderVO): Promise<void> {
    const id = props.accountId
    if (!id || cancellingId.value !== null) return
    try {
        await ElMessageBox.confirm(
            `确认撤销${row.stockName}（${row.stockCode}）的委托？\n委托价${
                row.price == null ? '市价' : fmtPrice(row.price)
            }，数量${Math.abs(row.quantity)}股`,
            '撤销委托',
            {
                confirmButtonText: '撤单',
                cancelButtonText: '取消',
                type: 'warning',
                customStyle: { whiteSpace: 'pre-line' }
            }
        )
    } catch {
        return
    }
    cancellingId.value = row.id
    try {
        await orderApi.cancel(id, row.id)
        ElMessage.success('撤单成功')
        await load()
    } catch (e) {
        ElMessage.error(e instanceof ApiError ? e.message : '撤单失败')
    } finally {
        cancellingId.value = null
    }
}

/**
 * 获取买卖方向文本
 * @param row 委托信息
 * @returns 买或卖
 */
function sideOf(row: OrderVO): '买' | '卖' {
    return row.quantity > 0 ? '买' : '卖'
}

/**
 * 获取状态标签类型
 * @param row 委托信息
 * @returns 标签类型
 */
function statusType(row: OrderVO): 'primary' | 'success' | 'info' | 'warning' {
    if (row.status === 1) return 'primary'
    if (row.status === 2) return 'warning'
    if (row.status === 3) return 'success'
    return 'info'
}

onMounted(() => {
    void load()
    offUpdate = marketSocket.onOrderUpdate(() => scheduleRefresh())
})

onBeforeUnmount(() => {
    offUpdate?.()
    offUpdate = null
    if (refreshTimer !== null) window.clearTimeout(refreshTimer)
    refreshTimer = null
})

watch(
    () => [props.accountId, props.code, props.status],
    () => {
        pageNum.value = 1
        void load()
    }
)

defineExpose({ reload })
</script>

<template>
    <div class="order-table">
        <el-table
            v-loading="loading"
            :data="rows"
            row-key="id"
            size="small"
            height="100%"
            style="width: 100%"
            :empty-text="error || '暂无委托'"
            :row-class-name="clickable ? 'is-clickable' : ''"
            @row-click="(row: OrderVO) => clickable && emit('open', row)"
        >
            <el-table-column label="委托时间" width="140">
                <template #default="{ row }">
                    <span class="num dim">{{ fmtDateTime(row.createTime) }}</span>
                </template>
            </el-table-column>

            <el-table-column label="代码" width="74">
                <template #default="{ row }">
                    <span class="num">{{ row.stockCode }}</span>
                </template>
            </el-table-column>

            <el-table-column prop="stockName" label="名称" min-width="88" show-overflow-tooltip />

            <el-table-column label="方向" width="52" align="center">
                <template #default="{ row }">
                    <span :class="row.quantity > 0 ? 'up' : 'down'">{{ sideOf(row) }}</span>
                </template>
            </el-table-column>

            <el-table-column label="委托价" width="78" align="right">
                <template #default="{ row }">
                    <span class="num">{{ row.price == null ? '市价' : fmtPrice(row.price) }}</span>
                </template>
            </el-table-column>

            <el-table-column label="委托量(股)" width="92" align="right">
                <template #default="{ row }">
                    <span class="num">{{ Math.abs(row.quantity) }}</span>
                </template>
            </el-table-column>

            <el-table-column label="已成交(股)" width="96" align="right">
                <template #default="{ row }">
                    <span class="num">{{ row.soldQuantity }}</span>
                </template>
            </el-table-column>

            <el-table-column label="类型" width="58" align="center">
                <template #default="{ row }">
                    <span class="dim">{{ row.type === 1 ? '限价' : '市价' }}</span>
                </template>
            </el-table-column>

            <el-table-column label="状态" width="88" align="center">
                <template #default="{ row }">
                    <el-tag size="small" :type="statusType(row)" effect="light">
                        {{ ORDER_STATUS_TEXT[row.status] ?? row.status }}
                    </el-tag>
                </template>
            </el-table-column>

            <el-table-column label="操作" width="68" align="center" fixed="right">
                <template #default="{ row }">
                    <el-tooltip
                        v-if="canCancel(row) && !cancelAllowed"
                        :content="cancelBlockReason"
                        placement="top"
                    >
                        <span class="cancel-disabled">撤单</span>
                    </el-tooltip>
                    <el-button
                        v-else-if="canCancel(row)"
                        text
                        size="small"
                        type="danger"
                        :loading="cancellingId === row.id"
                        @click.stop="cancel(row)"
                    >
                        撤单
                    </el-button>
                    <span v-else class="dim">--</span>
                </template>
            </el-table-column>
        </el-table>

        <div v-if="showPager" class="pager">
            <el-pagination
                small
                layout="total, prev, pager, next"
                :total="total"
                :page-size="size"
                :current-page="pageNum"
                :pager-count="5"
                @current-change="
                    (p: number) => {
                        pageNum = p
                        load()
                    }
                "
            />
        </div>
    </div>
</template>

<style scoped>
.order-table {
    display: flex;
    flex-direction: column;
    min-height: 0;
    height: 100%;
}

.num {
    font-variant-numeric: tabular-nums;
}

.dim {
    color: var(--text-secondary);
    font-size: 14px;
}

.pager {
    display: flex;
    justify-content: flex-end;
    padding: 6px 8px;
    border-top: 1px solid var(--divider);
}

.order-table :deep(.is-clickable) {
    cursor: pointer;
}

.cancel-disabled {
    display: inline-block;
    padding: 0 8px;
    font-size: 13px;
    color: var(--text-placeholder);
    cursor: not-allowed;
}
</style>
