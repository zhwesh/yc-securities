<script setup lang="ts">
import type { PositionVO } from '@/types'
import { fmtMoney, fmtPrice, fmtRatio, trendClass } from '@/utils/format'

const props = defineProps<{
    positions: PositionVO[]
    loading?: boolean
    // 紧凑模式，隐藏操作列与T+1列
    compact?: boolean
    // 行可点击
    clickable?: boolean
}>()

const emit = defineEmits<{
    (e: 'sell', position: PositionVO): void
    (e: 'open', position: PositionVO): void
}>()

/**
 * 判断可卖数量是否少于持仓数量
 * @param p 持仓信息
 * @returns 可卖数量不足时返回true
 */
function t1Warning(p: PositionVO): boolean {
    return p.availableQuantity < p.quantity
}

/**
 * 生成行样式类名
 * @param param0行数据
 * @returns 类名字符串
 */
function rowClass({ row }: { row: PositionVO }): string {
    const cls = row.profitLoss != null && row.profitLoss > 0 ? 'row-up' : ''
    return props.clickable ? `${cls} is-clickable` : cls
}

/**
 * 处理行点击并派发open事件
 * @param row 持仓信息
 */
function onRowClick(row: PositionVO): void {
    if (!props.clickable) return
    emit('open', row)
}
</script>

<template>
    <div class="position-table">
        <el-table
            v-loading="loading ?? false"
            :data="positions"
            row-key="code"
            size="small"
            height="100%"
            style="width: 100%"
            :row-class-name="rowClass"
            empty-text="暂无持仓"
            @row-click="onRowClick"
        >
            <el-table-column label="代码" width="76">
                <template #default="{ row }">
                    <span class="num">{{ row.code }}</span>
                </template>
            </el-table-column>

            <el-table-column prop="stockName" label="名称" min-width="88" show-overflow-tooltip />

            <el-table-column label="持仓(股)" width="88" align="right">
                <template #default="{ row }">
                    <span class="num">{{ row.quantity }}</span>
                </template>
            </el-table-column>

            <el-table-column label="可卖(股)" width="88" align="right">
                <template #default="{ row }">
                    <span class="num" :class="t1Warning(row) ? 'warn' : ''">
                        {{ row.availableQuantity }}
                    </span>
                </template>
            </el-table-column>

            <el-table-column label="成本价" width="82" align="right">
                <template #default="{ row }">
                    <span class="num">{{ fmtPrice(row.costPrice) }}</span>
                </template>
            </el-table-column>

            <el-table-column label="现价" width="82" align="right">
                <template #default="{ row }">
                    <span
                        class="num"
                        :class="
                            trendClass(
                                row.currentPrice && row.costPrice
                                    ? row.currentPrice - row.costPrice
                                    : null
                            )
                        "
                    >
                        {{ fmtPrice(row.currentPrice) }}
                    </span>
                </template>
            </el-table-column>

            <el-table-column label="市值" width="104" align="right">
                <template #default="{ row }">
                    <span class="num">{{ fmtMoney(row.marketValue) }}</span>
                </template>
            </el-table-column>

            <el-table-column label="浮动盈亏" width="104" align="right">
                <template #default="{ row }">
                    <span class="num strong" :class="trendClass(row.profitLoss)">
                        {{ fmtMoney(row.profitLoss) }}
                    </span>
                </template>
            </el-table-column>

            <el-table-column label="盈亏率" width="88" align="right">
                <template #default="{ row }">
                    <span class="num strong" :class="trendClass(row.profitLossRatio)">
                        {{ fmtRatio(row.profitLossRatio) }}
                    </span>
                </template>
            </el-table-column>

            <el-table-column v-if="!compact" label="操作" width="70" align="center" fixed="right">
                <template #default="{ row }">
                    <el-button text size="small" type="primary" @click.stop="emit('sell', row)">
                        卖出
                    </el-button>
                </template>
            </el-table-column>

            <el-table-column v-if="!compact" label="T+1" width="76" align="center">
                <template #default="{ row }">
                    <el-tooltip
                        v-if="t1Warning(row)"
                        content="当日买入的股票T+1后才可卖出"
                        placement="top"
                    >
                        <el-tag size="small" type="warning" effect="plain">可卖不足</el-tag>
                    </el-tooltip>
                    <span v-else class="dim">--</span>
                </template>
            </el-table-column>
        </el-table>
    </div>
</template>

<style scoped>
.position-table {
    height: 100%;
    min-height: 0;
}

.num {
    font-variant-numeric: tabular-nums;
}

.strong {
    font-weight: 600;
}

.dim {
    color: var(--text-placeholder);
    font-size: 14px;
}

.warn {
    color: #f5a524;
}

.position-table :deep(.is-clickable) {
    cursor: pointer;
}
</style>
