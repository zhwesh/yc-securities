import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { Result } from '@/types'
import { emitUnauthorized, getToken } from '@/utils/authToken'

/**
 * 业务异常
 */
export class ApiError extends Error {
    // 业务状态码
    code: number

    /**
     * 构造业务异常
     * @param message 错误信息
     * @param code 业务状态码
     */
    constructor(message: string, code = 0) {
        super(message)
        this.name = 'ApiError'
        this.code = code
    }
}

declare module 'axios' {
    export interface AxiosRequestConfig {
        // 静默模式，为true时不弹出默认错误提示
        silent?: boolean
    }
}

// 登录状态失效的信息特征
const UNAUTHORIZED_HINT = /未登录|登录已过期|登录过期|token/i

// 请求实例
const instance = axios.create({
    baseURL: '/api',
    timeout: 20000,
    headers: { 'Content-Type': 'application/json' }
})

// 请求拦截器，注入令牌
instance.interceptors.request.use((config) => {
    const token = getToken()
    if (token) {
        config.headers.Authorization = `Bearer ${token}`
    }
    return config
})

// 最近一次错误提示记录
let lastToast = { text: '', at: 0 }

/**
 * 弹出错误提示，相同内容在1.5秒内只提示一次
 * @param text 提示内容
 */
function toastError(text: string): void {
    const now = Date.now()
    if (lastToast.text === text && now - lastToast.at < 1500) return
    lastToast = { text, at: now }
    ElMessage({ type: 'error', message: text, grouping: true })
}

// 响应拦截器，拆解统一响应结构
instance.interceptors.response.use(
    (response) => {
        const body = response.data as Result | undefined

        if (!body || typeof body !== 'object' || !('code' in body)) {
            return response
        }

        if (body.code === 1) {
            return body.data as never
        }

        const msg = body.msg || '操作失败'
        if (!response.config.silent) toastError(msg)

        if (UNAUTHORIZED_HINT.test(msg)) {
            emitUnauthorized()
        }

        return Promise.reject(new ApiError(msg, body.code))
    },
    (error) => {
        if (axios.isCancel(error)) return Promise.reject(error)

        const status = error?.response?.status as number | undefined
        let msg = '网络异常，请稍后重试'

        if (status === 401 || status === 403) {
            msg = '登录已过期，请重新登录'
            emitUnauthorized()
        } else if (status === 404) {
            msg = '服务暂时不可用，请稍后重试'
        } else if (status && status >= 500) {
            msg = '服务异常，请稍后重试'
        } else if (error?.code === 'ECONNABORTED') {
            msg = '请求超时，请稍后重试'
        } else if (error?.response?.data?.msg) {
            msg = String(error.response.data.msg)
        }

        if (!error?.config?.silent) toastError(msg)
        return Promise.reject(new ApiError(msg, status ?? 0))
    }
)

/**
 * 发起请求并返回业务数据
 * @param config 请求配置
 * @returns 响应中的数据部分
 */
async function request<T>(config: AxiosRequestConfig): Promise<T> {
    return (await instance.request(config)) as unknown as T
}

/**
 * 请求入口
 */
export const http = {
    /**
     * 发起GET请求
     * @param url 请求路径
     * @param params 查询参数
     * @param config 附加请求配置
     * @returns 响应中的数据部分
     */
    get<T>(url: string, params?: Record<string, unknown>, config?: AxiosRequestConfig): Promise<T> {
        return request<T>({ ...config, url, method: 'GET', params })
    },

    /**
     * 发起POST请求
     * @param url 请求路径
     * @param data 请求体
     * @param config 附加请求配置
     * @returns 响应中的数据部分
     */
    post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
        return request<T>({ ...config, url, method: 'POST', data })
    },

    /**
     * 发起PUT请求
     * @param url 请求路径
     * @param data 请求体
     * @param config 附加请求配置
     * @returns 响应中的数据部分
     */
    put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
        return request<T>({ ...config, url, method: 'PUT', data })
    },

    /**
     * 发起DELETE请求，请求体置于config.data
     * @param url 请求路径
     * @param config 附加请求配置
     * @returns 响应中的数据部分
     */
    delete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
        return request<T>({ ...config, url, method: 'DELETE' })
    },

    /**
     * 上传表单数据
     * @param url 请求路径
     * @param formData 表单数据
     * @param config 附加请求配置
     * @returns 响应中的数据部分
     */
    upload<T>(url: string, formData: FormData, config?: AxiosRequestConfig): Promise<T> {
        return request<T>({
            ...config,
            url,
            method: 'POST',
            data: formData,
            headers: { 'Content-Type': 'multipart/form-data' },
            timeout: 60000
        })
    }
}

export default instance
