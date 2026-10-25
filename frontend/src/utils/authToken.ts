/**
 * 登录凭证与用户缓存的本地存储
 */

// 令牌存储键名
const TOKEN_KEY = 'yc:token'
// 用户信息存储键名
const USER_KEY = 'yc:user'

// 内存中的令牌副本
let cachedToken: string = readToken()

/**
 * 从本地存储读取令牌
 * @returns 令牌字符串
 */
function readToken(): string {
    try {
        return localStorage.getItem(TOKEN_KEY) ?? ''
    } catch {
        return ''
    }
}

/**
 * 获取当前令牌
 * @returns 令牌字符串
 */
export function getToken(): string {
    return cachedToken
}

/**
 * 保存令牌至内存与本地存储
 * @param token 令牌字符串
 */
export function setToken(token: string): void {
    cachedToken = token
    try {
        localStorage.setItem(TOKEN_KEY, token)
    } catch {
        /* 忽略存储异常 */
    }
}

/**
 * 清除令牌与用户缓存
 */
export function clearToken(): void {
    cachedToken = ''
    try {
        localStorage.removeItem(TOKEN_KEY)
        localStorage.removeItem(USER_KEY)
    } catch {
        /* 忽略存储异常 */
    }
}

/**
 * 读取本地缓存的用户信息
 * @returns 用户信息对象，无缓存时返回null
 */
export function getCachedUser<T>(): T | null {
    try {
        const raw = localStorage.getItem(USER_KEY)
        return raw ? (JSON.parse(raw) as T) : null
    } catch {
        return null
    }
}

/**
 * 写入用户信息至本地存储
 * @param user 用户信息对象
 */
export function setCachedUser(user: unknown): void {
    try {
        localStorage.setItem(USER_KEY, JSON.stringify(user))
    } catch {
        /* 忽略存储异常 */
    }
}

/************************************************** */

/**
 * 登录状态失效回调
 */
type Handler = () => void

// 回调集合
const handlers = new Set<Handler>()

/**
 * 注册登录状态失效回调
 * @param fn 回调函数
 * @returns 取消注册的函数
 */
export function onUnauthorized(fn: Handler): () => void {
    handlers.add(fn)
    return () => handlers.delete(fn)
}

// 单次失效事件的处理状态
let notifying = false

/**
 * 触发登录状态失效通知，清除令牌后依次调用回调
 */
export function emitUnauthorized(): void {
    if (notifying) return
    notifying = true
    clearToken()
    handlers.forEach((fn) => {
        try {
            fn()
        } catch {
            /* 忽略单个回调异常 */
        }
    })
    window.setTimeout(() => {
        notifying = false
    }, 1000)
}
