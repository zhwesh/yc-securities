import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { accountApi } from '@/api/account'
import { ApiError } from '@/api/http'

// 一小时对应的毫秒数
const HOUR = 3600 * 1000

/**
 * 生成在线状态记录用的存储键
 * @param accountId 资金账户标识
 * @returns 存储键名
 */
function expiryKey(accountId: number): string {
    return `yc:trade-online:${accountId}`
}

/**
 * 交易密码在线时长
 */
export type OnlineHours = 6 | 12 | 24

/**
 * 交易密码在线状态仓库
 */
export const useTradeAuthStore = defineStore('tradeAuth', () => {
    // 验证弹框显示状态
    const visible = ref(false)
    // 验证请求进行中标记
    const busy = ref(false)
    // 验证错误信息
    const error = ref('')
    // 待验证的资金账户标识
    const accountId = ref<number | null>(null)
    // 保持在线时长
    const onlineHours = ref<OnlineHours>(24)
    // 最近一次验证通过的时间戳
    const lastVerifiedAt = ref(0)

    // 弹框结果回调
    let resolver: ((ok: boolean) => void) | null = null

    // 当前账户是否处于在线状态
    const isOnline = computed(() => {
        void lastVerifiedAt.value
        return accountId.value !== null ? readOnline(accountId.value) : false
    })

    /**
     * 读取指定账户的在线状态
     * @param id 资金账户标识
     * @returns 处于有效期时返回true
     */
    function readOnline(id: number): boolean {
        try {
            const raw = localStorage.getItem(expiryKey(id))
            if (!raw) return false
            return Number(raw) > Date.now()
        } catch {
            return false
        }
    }

    /**
     * 记录指定账户的在线状态
     * @param id 资金账户标识
     * @param hours 保持在线时长
     */
    function markOnline(id: number, hours: OnlineHours): void {
        try {
            localStorage.setItem(expiryKey(id), String(Date.now() + hours * HOUR))
        } catch {
            /* 忽略存储异常 */
        }
        lastVerifiedAt.value = Date.now()
    }

    /**
     * 清除在线状态记录
     * @param id 资金账户标识，省略时清除全部记录
     */
    function invalidate(id?: number): void {
        try {
            if (id === undefined) {
                Object.keys(localStorage)
                    .filter((k) => k.startsWith('yc:trade-online:'))
                    .forEach((k) => localStorage.removeItem(k))
            } else {
                localStorage.removeItem(expiryKey(id))
            }
        } catch {
            /* 忽略存储异常 */
        }
        lastVerifiedAt.value = Date.now()
    }

    /**
     * 获取在线状态剩余分钟数
     * @param id 资金账户标识
     * @returns 剩余分钟数
     */
    function remainingMinutes(id: number): number {
        try {
            const raw = localStorage.getItem(expiryKey(id))
            if (!raw) return 0
            return Math.max(0, Math.floor((Number(raw) - Date.now()) / 60000))
        } catch {
            return 0
        }
    }

    /**
     * 打开验证弹框并等待结果
     * @param id 资金账户标识
     * @returns 验证通过返回true
     */
    function open(id: number): Promise<boolean> {
        accountId.value = id
        error.value = ''
        visible.value = true
        return new Promise<boolean>((resolve) => {
            resolver = resolve
        })
    }

    /**
     * 确保账户处于在线状态，未在线时弹出验证框
     * @param id 资金账户标识
     * @returns 验证通过返回true
     */
    async function ensure(id: number): Promise<boolean> {
        if (readOnline(id)) return true
        return open(id)
    }

    /**
     * 强制弹出验证框
     * @param id 资金账户标识
     * @returns 验证通过返回true
     */
    function prompt(id: number): Promise<boolean> {
        return open(id)
    }

    /**
     * 提交交易密码进行验证
     * @param password 交易密码
     * @returns 验证通过返回true
     */
    async function submit(password: string): Promise<boolean> {
        const id = accountId.value
        if (!id) return false
        if (!password) {
            error.value = '请输入交易密码'
            return false
        }
        busy.value = true
        error.value = ''
        try {
            await accountApi.verifyTradePassword(id, password, onlineHours.value, true)
            markOnline(id, onlineHours.value)
            return true
        } catch (e) {
            error.value = e instanceof ApiError ? e.message : '验证失败，请重试'
            return false
        } finally {
            busy.value = false
        }
    }

    /**
     * 关闭弹框并返回验证通过
     */
    function confirmSuccess(): void {
        visible.value = false
        resolver?.(true)
        resolver = null
    }

    /**
     * 关闭弹框并返回验证未通过
     */
    function cancel(): void {
        if (busy.value) return
        visible.value = false
        error.value = ''
        resolver?.(false)
        resolver = null
    }

    return {
        visible,
        busy,
        error,
        accountId,
        onlineHours,
        lastVerifiedAt,
        isOnline,
        readOnline,
        remainingMinutes,
        markOnline,
        invalidate,
        ensure,
        prompt,
        submit,
        confirmSuccess,
        cancel
    }
})
