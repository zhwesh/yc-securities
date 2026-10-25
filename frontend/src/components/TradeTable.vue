<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { tradeApi } from '@/api/account'
import { ApiError } from '@/api/http'
import type { TradeVO } from '@/types'
import { fmtDateTime, fmtMoney, fmtPrice } from '@/utils/format'

const props = defineProps<{
    accountId: number | null
    code?: string
    pageSize?: number
    showPager?: boolean
    // 行可点击
    clickable?: boolean
}>()

const emit = defineEmits<{
    (e: 'open', trade: TradeVO): void
}>()

// 当前页记录
const rows = ref<TradeVO[]>([])
// 记录总数
const total = ref(0)
// 当前页码
const pageNum = ref(1)
// 加载状态
const loading = ref(false)
// 错误信息
const error = ref('')

// 每页条数
const size = computed(() => props.pageSize ?? 20)

/**
 * 加载成交记录
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
        const page = await tradeApi.list({
            accountId: id,
            code: props.code || undefined,
            pageNum: pageNum.value,
            pageSize: size.value
        })
        rows.value = page.records
        total.value = page.total
    } catch (e) {
        error.value = e instanceof ApiError ? e.message : '成交记录加载失败'
        rows.value = []
        total.value = 0
    } finally {
        loading.value = false
    }
}

onMounted(load)

watch(
    () => [props.accountId, props.code],
    () => {
        pageNum.value = 1
        void load()
    }
)

defineExpose({ reload: load })
</script>

<template>
    <div class="trade-table">
        <el-table
            v-loading="loading"
            :data="rows"
            row-key="id"
            size="small"
            height="100%"
            style="width: 100%"
            :empty-text="error || '暂无成交记录'"
            :row-class-name="clickable ? 'is-clickable' : ''"
            @row-click="(row: TradeVO) => clickable && emit('open', row)"
        >
            <el-table-column label="成交时间" width="146">
                <template #default="{ row }">
                    <span class="num dim">{{ fmtDateTime(row.createTime) }}</span>
                </template>
            </el-table-column>

            <el-table-column label="代码" width="76">
                <template #default="{ row }">
                    <span class="num">{{ row.stockCode }}</span>
                </template>
            </el-table-column>

            <el-table-column prop="stockName" label="名称" min-width="90" show-overflow-tooltip />

            <el-table-column label="方向" width="54" align="center">
                <template #default="{ row }">
                    <span :class="row.quantity > 0 ? 'up' : 'down'">
                        {{ row.quantity > 0 ? '买入' : '卖出' }}
                    </span>
                </template>
            </el-table-column>

            <el-table-column label="成交价" width="86" align="right">
                <template #default="{ row }">
                    <span class="num">{{ fmtPrice(row.price) }}</span>
                </template>
            </el-table-column>

            <el-table-column label="成交量(股)" width="96" align="right">
                <template #default="{ row }">
                    <span class="num">{{ Math.abs(row.quantity) }}</span>
                </template>
            </el-table-column>

            <el-table-column label="成交金额" width="110" align="right">
                <template #default="{ row }">
                    <span class="num">{{ fmtMoney(Math.abs(row.amount)) }}</span>
                </template>
            </el-table-column>

            <el-table-column label="委托编号" width="96" align="right">
                <template #default="{ row }">
                    <span class="num dim">{{ row.orderId }}</span>
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
.trade-table {
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

.trade-table :deep(.is-clickable) {
    cursor: pointer;
}
</style>
