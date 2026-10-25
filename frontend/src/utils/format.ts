/**
 * 展示层格式化工具
 */

// 空值占位符
const EMPTY = '--'

/**
 * 判断值是否为空
 * 空值包含null、undefined、空字符串与NaN
 * @param v 待判断的值
 * @returns 为空时返回true
 */
export function isEmpty(v: unknown): boolean {
    return v === null || v === undefined || v === '' || (typeof v === 'number' && Number.isNaN(v))
}

/**
 * 格式化价格，保留指定小数位
 * @param v 价格
 * @param digits 小数位数
 * @returns 定点小数字符串
 */
export function fmtPrice(v: number | null | undefined, digits = 2): string {
    if (isEmpty(v)) return EMPTY
    return Number(v).toFixed(digits)
}

/**
 * 格式化百分数，输入值已为百分数
 * @param v 百分数值
 * @param digits 小数位数
 * @returns 带正负号的百分数字符串
 */
export function fmtPercent(v: number | null | undefined, digits = 2): string {
    if (isEmpty(v)) return EMPTY
    const num = Number(v)
    const str = num.toFixed(digits)
    return num > 0 ? `+${str}%` : `${str}%`
}

/**
 * 格式化涨跌额，保留正负号
 * @param v 涨跌额
 * @param digits 小数位数
 * @returns 带正负号的数值字符串
 */
export function fmtChange(v: number | null | undefined, digits = 2): string {
    if (isEmpty(v)) return EMPTY
    const num = Number(v)
    const str = num.toFixed(digits)
    return num > 0 ? `+${str}` : str
}

/**
 * 格式化比例值，输入0.1234输出+12.34%
 * @param v 比例值
 * @param digits 小数位数
 * @returns 带正负号的百分数字符串
 */
export function fmtRatio(v: number | null | undefined, digits = 2): string {
    if (isEmpty(v)) return EMPTY
    return fmtPercent(Number(v) * 100, digits)
}

/**
 * 格式化股数，带正负号与千分位
 * @param v 股数
 * @returns 带符号的千分位字符串
 */
export function fmtShares(v: number | null | undefined): string {
    if (isEmpty(v)) return EMPTY
    const num = Math.round(Number(v))
    return (num > 0 ? '+' : '') + num.toLocaleString('en-US')
}

/**
 * 格式化手数，超过一万手转换为万手，超过一亿手转换为亿手
 * @param v 手数
 * @returns 手数字符串
 */
export function fmtLots(v: number | null | undefined): string {
    if (isEmpty(v)) return EMPTY
    const num = Number(v)
    const abs = Math.abs(num)
    if (abs >= 1e8) return `${(num / 1e8).toFixed(2)}亿手`
    if (abs >= 1e4) return `${(num / 1e4).toFixed(2)}万手`
    return `${Math.round(num)}手`
}

/**
 * 格式化金额，超过一万转换为万，超过一亿转换为亿
 * @param v 金额，单位为元
 * @returns 金额字符串
 */
export function fmtAmount(v: number | null | undefined): string {
    if (isEmpty(v)) return EMPTY
    const num = Number(v)
    const abs = Math.abs(num)
    if (abs >= 1e8) return `${(num / 1e8).toFixed(2)}亿`
    if (abs >= 1e4) return `${(num / 1e4).toFixed(2)}万`
    return num.toFixed(2)
}

/**
 * 格式化金额，固定小数位并添加千分位
 * @param v 金额，单位为元
 * @param digits 小数位数
 * @returns 千分位金额字符串
 */
export function fmtMoney(v: number | null | undefined, digits = 2): string {
    if (isEmpty(v)) return EMPTY
    return Number(v).toLocaleString('en-US', {
        minimumFractionDigits: digits,
        maximumFractionDigits: digits
    })
}

/**
 * 获取涨跌样式类名
 * @param v 涨跌值
 * @returns up、down或flat
 */
export function trendClass(v: number | null | undefined): 'up' | 'down' | 'flat' {
    if (isEmpty(v)) return 'flat'
    const num = Number(v)
    if (num > 0) return 'up'
    if (num < 0) return 'down'
    return 'flat'
}

/**
 * 格式化日期时间，输出yyyy-MM-dd HH:mm:ss
 * @param v ISO格式或空格分隔的时间字符串
 * @returns 日期时间字符串
 */
export function fmtDateTime(v: string | null | undefined): string {
    if (!v) return EMPTY
    return v.replace('T', ' ').slice(0, 19)
}

/**
 * 格式化日期，输出yyyy-MM-dd
 * @param v 时间字符串
 * @returns 日期字符串
 */
export function fmtDate(v: string | null | undefined): string {
    if (!v) return EMPTY
    return String(v).slice(0, 10)
}

/**
 * 掩码手机号，保留前三位与后四位
 * @param phone 手机号
 * @returns 掩码后的手机号
 */
export function maskPhone(phone: string | null | undefined): string {
    if (!phone || phone.length < 7) return phone ?? EMPTY
    return `${phone.slice(0, 3)}****${phone.slice(-4)}`
}

/**
 * 获取板块涨跌停幅度，板块未知时按代码前缀推断
 * @param board 板块名称
 * @param code 股票代码
 * @returns 涨跌停幅度百分数
 */
export function limitPercent(board: string | undefined | null, code?: string): number {
    if (board === '创业板' || board === '科创板') return 20
    if (board === '北交所') return 30
    if (board === '主板') return 10
    if (code) {
        if (/^(300|301)/.test(code)) return 20
        if (/^(688|689)/.test(code)) return 20
        if (/^(8|4|9)/.test(code)) return 30
    }
    return 10
}
