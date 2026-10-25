import { http } from './http'
import type { LoginVO, UserVO } from '@/types'

/**
 * 用户与登录接口
 */
export const userApi = {
    /**
     * 发送短信验证码
     * @param phone 手机号
     * @returns 无返回值
     */
    sendSmsCode(phone: string): Promise<void> {
        return http.get<void>('/user/code', { phone })
    },

    /**
     * 使用用户名与密码登录
     * @param username 用户名
     * @param password 登录密码
     * @returns 登录信息
     */
    loginByUsername(username: string, password: string): Promise<LoginVO> {
        return http.post<LoginVO>('/user/login/username', { username, password })
    },

    /**
     * 使用手机号与验证码登录
     * @param phone 手机号
     * @param code 验证码
     * @returns 登录信息
     */
    loginByPhone(phone: string, code: string): Promise<LoginVO> {
        return http.post<LoginVO>('/user/login/phone', { phone, code })
    },

    /**
     * 注册账号，成功后返回登录信息
     * @param data 注册参数
     * @returns 登录信息
     */
    register(data: {
        phone: string
        code: string
        username: string
        password: string
        name: string
        idNumber: string
    }): Promise<LoginVO> {
        return http.post<LoginVO>('/user/register', data)
    },

    /**
     * 重置登录密码
     * @param phone 手机号
     * @param code 验证码
     * @param newPassword 新密码
     * @returns 无返回值
     */
    resetPassword(phone: string, code: string, newPassword: string): Promise<void> {
        return http.put<void>('/user/password/reset', { phone, code, newPassword })
    },

    /**
     * 查询当前登录用户信息
     * @returns 用户信息
     */
    current(): Promise<UserVO> {
        return http.get<UserVO>('/user')
    },

    /**
     * 修改当前用户信息
     * @param data 修改参数
     * @returns 无返回值
     */
    update(data: {
        username?: string
        image?: string
        phone?: string
        code?: string
    }): Promise<void> {
        return http.put<void>('/user/update', data)
    },

    /**
     * 上传头像文件
     * @param file 图片文件，不超过2MB
     * @returns 头像访问地址
     */
    uploadAvatar(file: File): Promise<string> {
        const fd = new FormData()
        fd.append('file', file)
        return http.upload<string>('/user/avatar', fd)
    }
}
