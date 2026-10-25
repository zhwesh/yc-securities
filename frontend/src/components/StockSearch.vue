<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { stockApi } from '@/api/market'
import type { StockVO } from '@/types'

const props = withDefaults(
    defineProps<{
        // 为false时仅触发select事件，不执行路由跳转
        navigate?: boolean
        placeholder?: string
        width?: string
    }>(),
    {
        navigate: true,
        placeholder: '代码/名称',
        width: '220px'
    }
)

const emit = defineEmits<{
    (e: 'select', stock: StockVO): void
}>()

const router = useRouter()
// 搜索关键字
const keyword = ref('')

/**
 * 候选选项
 */
interface Suggestion extends StockVO {
    // 选项显示文本
    value: string
}

/**
 * 查询候选股票
 * @param query 输入关键字
 * @param cb 回调函数，接收候选列表
 */
async function fetchSuggestions(query: string, cb: (items: Suggestion[]) => void): Promise<void> {
    const kw = query.trim()
    if (kw.length < 1) {
        cb([])
        return
    }
    try {
        const page = await stockApi.list(kw, 1, 10)
        cb(
            page.records.map((s) => ({
                ...s,
                value: `${s.code} ${s.name}`
            }))
        )
    } catch {
        cb([])
    }
}

/**
 * 处理选中候选股票
 * @param item 候选项
 */
function onSelect(item: Record<string, unknown>): void {
    const code = item.code as string
    const name = item.name as string
    keyword.value = ''
    if (!code) return
    emit('select', { code, name })
    if (props.navigate) {
        void router.push({ name: 'stock-detail', params: { code } })
    }
}

/**
 * 处理回车，输入为六位代码时直接跳转
 */
function onEnter(): void {
    const kw = keyword.value.trim()
    if (/^\d{6}$/.test(kw)) {
        keyword.value = ''
        emit('select', { code: kw, name: kw })
        if (props.navigate) {
            void router.push({ name: 'stock-detail', params: { code: kw } })
        }
    }
}
</script>

<template>
    <el-autocomplete
        v-model="keyword"
        class="stock-search"
        :style="{ width }"
        popper-class="stock-search-popper"
        :placeholder="placeholder"
        :fetch-suggestions="fetchSuggestions"
        :trigger-on-focus="false"
        value-key="value"
        clearable
        @select="onSelect"
        @keyup.enter="onEnter"
    >
        <template #prefix>
            <el-icon><Search /></el-icon>
        </template>
        <template #default="{ item }">
            <div class="suggest-row">
                <span class="code num">{{ item.code }}</span>
                <span class="name">{{ item.name }}</span>
            </div>
        </template>
    </el-autocomplete>
</template>

<style scoped>
.stock-search {
    width: 220px;
}

.suggest-row {
    display: flex;
    align-items: center;
    gap: 10px;
}

.suggest-row .code {
    color: var(--text-secondary);
    font-size: 14px;
    min-width: 52px;
}
</style>
