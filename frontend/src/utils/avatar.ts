/**
 * 头像加载失败记录
 * 记录加载失败的资源地址，有效期内不再重复请求
 */

// 本地存储键名
const LS_KEY = 'yc:avatar-failed'
// 记录有效期，单位毫秒
const TTL = 60 * 60 * 1000

/**
 * 失败记录表，键为资源地址，值为记录时间戳
 */
type FailedMap = Record<string, number>

/**
 * 读取失败记录表
 * @returns 失败记录表
 */
function read(): FailedMap {
    try {
        const raw = localStorage.getItem(LS_KEY)
        const obj = raw ? (JSON.parse(raw) as FailedMap) : {}
        return obj && typeof obj === 'object' ? obj : {}
    } catch {
        return {}
    }
}

/**
 * 写入失败记录表，同时清除过期记录
 * @param map 失败记录表
 */
function write(map: FailedMap): void {
    try {
        const entries = Object.entries(map)
            .filter(([, ts]) => Date.now() - ts < TTL)
            .slice(-20)
        localStorage.setItem(LS_KEY, JSON.stringify(Object.fromEntries(entries)))
    } catch {
        /* 忽略存储异常 */
    }
}

/**
 * 判断资源是否处于失败有效期内
 * @param url 资源地址
 * @returns 处于有效期内返回true
 */
export function isAvatarFailed(url: string): boolean {
    const ts = read()[url]
    return typeof ts === 'number' && Date.now() - ts < TTL
}

/**
 * 记录资源加载失败
 * @param url 资源地址
 */
export function markAvatarFailed(url: string): void {
    if (!url) return
    const map = read()
    map[url] = Date.now()
    write(map)
}

/**
 * 清除失败记录
 * @param url 资源地址，省略时清除全部记录
 */
export function clearAvatarFailed(url?: string): void {
    if (!url) {
        try {
            localStorage.removeItem(LS_KEY)
        } catch {
            /* 忽略存储异常 */
        }
        return
    }
    const map = read()
    delete map[url]
    write(map)
}
