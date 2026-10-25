import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { accountApi, positionApi } from '@/api/account'
import { ApiError } from '@/api/http'
import type { AccountVO, PositionVO } from '@/types'

// 当前资金账户本地存储键名
const CURRENT_KEY = 'yc:currentAccountId'

/**
 * 读取当前资金账户标识
 * @returns 账户标识，无记录时返回null
 */
function readCurrentId(): number | null {
    try {
        const raw = localStorage.getItem(CURRENT_KEY)
        const num = raw ? Number(raw) : NaN
        return Number.isFinite(num) && num > 0 ? num : null
    } catch {
        return null
    }
}

/**
 * 写入当前资金账户标识
 * @param id 账户标识，传入null时清除记录
 */
function writeCurrentId(id: number | null): void {
    try {
        if (id === null) localStorage.removeItem(CURRENT_KEY)
        else localStorage.setItem(CURRENT_KEY, String(id))
    } catch {
        /* 忽略存储异常 */
    }
}

/**
 * 资金账户仓库
 */
export const useAccountStore = defineStore('account', () => {
    // 资金账户列表
    const accounts = ref<AccountVO[]>([])
    // 当前资金账户标识
    const currentId = ref<number | null>(readCurrentId())
    // 当前账户持仓
    const positions = ref<PositionVO[]>([])
    // 账户列表加载状态
    const loading = ref(false)
    // 持仓加载状态
    const positionsLoading = ref(false)
    // 持仓查询需要交易密码的标记
    const needTradeAuth = ref(false)
    // 账户列表已加载标记
    const loaded = ref(false)

    // 当前资金账户
    const current = computed<AccountVO | null>(() => {
        if (!accounts.value.length) return null
        return accounts.value.find((a) => a.id === currentId.value) ?? accounts.value[0]
    })

    // 是否存在资金账户
    const hasAccount = computed(() => accounts.value.length > 0)

    // 持仓市值合计
    const positionMarketValue = computed(() =>
        positions.value.reduce((sum, p) => sum + (p.marketValue ?? 0), 0)
    )

    // 浮动盈亏合计
    const totalProfitLoss = computed(() =>
        positions.value.reduce((sum, p) => sum + (p.profitLoss ?? 0), 0)
    )

    // 总资产，等于可用资产与持仓市值之和
    const totalAssets = computed(
        () => (current.value?.availableAssets ?? 0) + positionMarketValue.value
    )

    // 可用资产
    const availableAssets = computed(() => current.value?.availableAssets ?? 0)

    /**
     * 切换当前资金账户并加载持仓
     * @param id 账户标识
     */
    function selectAccount(id: number): void {
        if (currentId.value === id) return
        currentId.value = id
        writeCurrentId(id)
        positions.value = []
        needTradeAuth.value = false
        void loadPositions()
    }

    /**
     * 加载资金账户列表并校正当前账户
     * @returns 资金账户列表
     */
    async function loadAccounts(): Promise<AccountVO[]> {
        loading.value = true
        try {
            const list = await accountApi.list()
            accounts.value = list
            loaded.value = true
            if (!list.length) {
                currentId.value = null
                writeCurrentId(null)
            } else if (!list.some((a) => a.id === currentId.value)) {
                currentId.value = list[0].id
                writeCurrentId(currentId.value)
            }
            return list
        } finally {
            loading.value = false
        }
    }

    /**
     * 加载当前账户持仓
     */
    async function loadPositions(): Promise<void> {
        const id = currentId.value
        if (!id) {
            positions.value = []
            return
        }
        positionsLoading.value = true
        try {
            positions.value = await positionApi.list(id, true)
            needTradeAuth.value = false
        } catch (e) {
            if (e instanceof ApiError && e.message.includes('交易密码')) {
                needTradeAuth.value = true
                positions.value = []
            } else {
                console.warn('[position] 加载失败', e)
            }
        } finally {
            positionsLoading.value = false
        }
    }

    /**
     * 刷新资金账户列表
     */
    async function refreshAccount(): Promise<void> {
        try {
            accounts.value = await accountApi.list()
        } catch {
            /* 忽略请求异常 */
        }
    }

    /**
     * 重置仓库状态
     */
    function reset(): void {
        accounts.value = []
        positions.value = []
        currentId.value = null
        needTradeAuth.value = false
        loaded.value = false
        writeCurrentId(null)
    }

    return {
        accounts,
        currentId,
        positions,
        loading,
        positionsLoading,
        needTradeAuth,
        loaded,
        current,
        hasAccount,
        positionMarketValue,
        totalProfitLoss,
        totalAssets,
        availableAssets,
        selectAccount,
        loadAccounts,
        loadPositions,
        refreshAccount,
        reset
    }
})
