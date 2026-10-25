/**
 * 交易规则计算
 */

// 佣金率，买卖双向收取
const COMMISSION_RATE = 0.00025
// 印花税率，仅卖出收取
const STAMP_TAX_RATE = 0.0005
// 过户费率，买卖双向收取
const TRANSFER_RATE = 0.00001

/**
 * 费率集合
 */
export const FEE_RATE = {
    commission: COMMISSION_RATE,
    stampTax: STAMP_TAX_RATE,
    transfer: TRANSFER_RATE
} as const

/**
 * 买卖方向
 */
export type TradeSide = 'buy' | 'sell'

/**
 * 单笔最小委托数量，单位为股
 */
export const LOT_SIZE = 100

/**
 * 计算预估费用
 * @param amount 成交金额
 * @param side 买卖方向
 * @returns 费用金额，保留两位小数
 */
export function estimateFee(amount: number, side: TradeSide): number {
    if (!amount || amount <= 0) return 0
    const rate = side === 'buy'
        ? COMMISSION_RATE + TRANSFER_RATE
        : COMMISSION_RATE + STAMP_TAX_RATE + TRANSFER_RATE
    return round2(amount * rate)
}

/**
 * 四舍五入至两位小数
 * @param n 待处理数值
 * @returns 两位小数结果
 */
export function round2(n: number): number {
    return Math.round((n + Number.EPSILON) * 100) / 100
}

/**
 * 生成委托幂等键
 * @returns UUID字符串
 */
export function nextOrderKey(): string {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
        return crypto.randomUUID()
    }
    const bytes = crypto.getRandomValues(new Uint8Array(16))
    return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
}
