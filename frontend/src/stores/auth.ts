import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { userApi } from '@/api/user'
import type { LoginVO, UserVO } from '@/types'
import { clearToken, getCachedUser, getToken, setCachedUser, setToken } from '@/utils/authToken'
import { marketSocket } from '@/ws/marketSocket'

/**
 * 当前用户类型
 */
export type CurrentUser = UserVO

/**
 * 登录状态仓库
 */
export const useAuthStore = defineStore('auth', () => {
    // 访问令牌
    const token = ref<string>(getToken())
    // 当前用户信息
    const user = ref<CurrentUser | null>(getCachedUser<CurrentUser>())
    // 初始化完成标记
    const ready = ref(false)

    // 是否已登录
    const isLoggedIn = computed(() => !!token.value)
    // 显示名称
    const displayName = computed(() => user.value?.name || user.value?.username || '未登录')
    // 头像地址
    const avatar = computed(() => user.value?.image ?? '')

    /**
     * 应用登录结果并建立行情连接
     * @param vo 登录响应
     */
    function applyLogin(vo: LoginVO): void {
        setToken(vo.token)
        token.value = vo.token
        const curUser: CurrentUser = {
            id: vo.id,
            username: vo.username,
            name: vo.name,
            image: vo.image,
            phone: vo.phone,
            createTime: vo.createTime
        }
        user.value = curUser
        setCachedUser(curUser)
        marketSocket.connect(vo.token)
    }

    /**
     * 应用用户信息并写入本地缓存
     * @param u 用户信息
     */
    function applyUser(u: CurrentUser): void {
        user.value = u
        setCachedUser(u)
    }

    /**
     * 使用用户名与密码登录
     * @param username 用户名
     * @param password 登录密码
     */
    async function loginByUsername(username: string, password: string): Promise<void> {
        applyLogin(await userApi.loginByUsername(username, password))
    }

    /**
     * 使用手机号与验证码登录
     * @param phone 手机号
     * @param code 验证码
     */
    async function loginByPhone(phone: string, code: string): Promise<void> {
        applyLogin(await userApi.loginByPhone(phone, code))
    }

    /**
     * 注册账号并应用登录结果
     * @param data 注册参数
     */
    async function register(data: {
        phone: string
        code: string
        username: string
        password: string
        name: string
        idNumber: string
    }): Promise<void> {
        applyLogin(await userApi.register(data))
    }

    /**
     * 刷新当前用户资料
     * @returns 刷新成功返回true
     */
    async function fetchProfile(): Promise<boolean> {
        if (!token.value) return false
        try {
            const u = await userApi.current()
            applyUser(u)
            return true
        } catch {
            return false
        }
    }

    /**
     * 恢复登录状态并建立行情连接
     */
    async function init(): Promise<void> {
        if (ready.value) return
        ready.value = true
        if (!token.value) return
        const ok = await fetchProfile()
        if (ok) {
            marketSocket.connect(token.value)
        }
    }

    /**
     * 退出登录，清除令牌与用户信息并断开行情连接
     */
    function logout(): void {
        clearToken()
        token.value = ''
        user.value = null
        marketSocket.disconnect()
    }

    /**
     * 处理登录状态失效，清除本地状态并断开行情连接
     */
    function handleExpired(): void {
        token.value = ''
        user.value = null
        marketSocket.disconnect()
    }

    return {
        token,
        user,
        ready,
        isLoggedIn,
        displayName,
        avatar,
        applyLogin,
        applyUser,
        loginByUsername,
        loginByPhone,
        register,
        fetchProfile,
        init,
        logout,
        handleExpired
    }
})
