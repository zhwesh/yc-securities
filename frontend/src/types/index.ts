/**
 * 后端接口数据结构定义
 * 字段名称与后端返回结构保持一致
 */

/**
 * 统一响应结构
 */
export interface Result<T = unknown> {
    // 业务状态，1表示成功，0表示失败
    code: number
    // 错误信息
    msg: string | null
    // 业务数据
    data: T
}

/**
 * 分页响应结构
 */
export interface PageResult<T> {
    // 记录总数
    total: number
    // 当前页记录
    records: T[]
}

/************************************************** */

/**
 * 登录响应
 */
export interface LoginVO {
    token: string
    id: number
    username: string
    name: string
    image: string | null
    phone: string
    createTime: string
}

/**
 * 用户信息
 */
export interface UserVO {
    id: number
    username: string
    name: string
    image: string | null
    phone: string
    createTime: string
}

/**
 * 用户名登录请求
 */
export interface UsernameLoginDTO {
    username: string
    password: string
}

/**
 * 手机号登录请求
 */
export interface PhoneLoginDTO {
    phone: string
    code: string
}

/**
 * 注册请求
 */
export interface RegisterDTO {
    phone: string
    code: string
    username: string
    password: string
    name: string
    idNumber: string
}

/**
 * 重置密码请求
 */
export interface ResetPasswordDTO {
    phone: string
    code: string
    newPassword: string
}

/**
 * 修改用户信息请求
 */
export interface UpdateUserDTO {
    username?: string
    image?: string
    phone?: string
    code?: string
}

/************************************************** */

/**
 * 资金账户信息
 */
export interface AccountVO {
    id: number
    // 可用资产，等于现金总额减去未成交买单占用
    availableAssets: number
    // 已开通板块
    boards: string[]
    // 账户状态，1表示正常，2表示冻结
    status: number
    createTime: string
}

/**
 * 开通资金账户请求
 */
export interface OpenAccountDTO {
    password: string
}

/**
 * 开通板块请求
 */
export interface OpenBoardDTO {
    accountId: number
    boards: string[]
}

/**
 * 修改交易密码请求
 */
export interface UpdateTradePasswordDTO {
    accountId: number
    oldPassword: string
    newPassword: string
}

/**
 * 验证交易密码请求
 */
export interface VerifyTradePasswordDTO {
    accountId: number
    tradePassword: string
    // 保持在线时长，取值6、12、24
    onlineHours: 6 | 12 | 24
}

/**
 * 入金请求
 */
export interface DepositDTO {
    accountId: number
    amount: number
}

/**
 * 出金请求
 */
export interface WithdrawDTO {
    accountId: number
    amount: number
}

/************************************************** */

/**
 * 银行卡信息
 */
export interface BankCardVO {
    id: number
    accountId: number
    cardNumber: string
    bankName: string
    // 银行预留手机号，已掩码
    phone: string
    bindTime: string
}

/**
 * 绑定银行卡请求
 */
export interface BindBankCardDTO {
    accountId: number
    cardNumber: string
    bankName: string
    phone: string
    code: string
}

/**
 * 解绑银行卡请求
 */
export interface DeleteBankCardDTO {
    accountId: number
    cardNumber: string
    phone: string
    code: string
}

/**
 * 校验银行卡与预留手机号请求
 */
export interface VerifyBankCardDTO {
    cardNumber: string
    phone: string
}

/************************************************** */

/**
 * 股票摘要信息
 */
export interface StockVO {
    code: string
    name: string
}

/**
 * 股票详情信息
 */
export interface StockDetailVO {
    code: string
    name: string
    board: string
    listDate: string
    totalShares: number
    circulateShares: number
    bookValue: number | null
    eps: number | null
    initialPrice: number
}

/************************************************** */

/**
 * 行情快照
 */
export interface StockQuoteVO {
    code: string
    name: string
    price: number
    change: number
    changePercent: number
    open: number | null
    high: number | null
    low: number | null
    preClose: number
    // 成交量，单位手
    volume: number
    amount: number
    // 换手率，单位百分数
    turnoverRate: number | null
    amplitude: number | null
    volumeRatio: number | null
    pe: number | null
    marketValue: number
    circulateMarketValue: number
    pb: number | null
}

/**
 * 指数行情
 */
export interface IndexVO {
    code: string
    name: string
    price: number
    change: number
    changePercent: number
    // 成交量，单位手
    volume: number
    amount: number
}

/**
 * 盘口档位
 */
export interface OrderBookItemVO {
    price: number
    // 挂单量，单位手
    volume: number
}

/**
 * 盘口快照
 */
export interface OrderBookVO {
    code: string
    depth: number
    // 卖盘，按价格升序
    asks: OrderBookItemVO[]
    // 买盘，按价格降序
    bids: OrderBookItemVO[]
}

/**
 * 逐笔成交明细
 */
export interface StockTradeDetailVO {
    // 成交时间，格式HH:mm:ss
    time: string
    price: number
    // 成交量，单位股
    volume: number
    // 主动方向，0表示主动买，1表示主动卖
    direction?: number
}

/**
 * 日线数据
 */
export interface KLineData {
    // 交易日期，格式yyyy-MM-dd
    time: string
    open: number
    high: number
    low: number
    close: number
    // 成交量，单位手
    volume: number
    // 成交额，单位元
    turnover: number
}

/**
 * 日线响应
 */
export interface KLineVO {
    code: string
    // 周期，1日线2周线3月线4季线5年线
    period: number
    hasMore: boolean
    kLine: KLineData[]
}

/**
 * 分时数据点
 */
export interface MinuteData {
    // 秒级时间戳，按UTC换算存储
    time: number
    price: number
    avgPrice: number
    // 该分钟成交量，单位手
    volume: number
}

/**
 * 分时响应
 */
export interface MinuteVO {
    code: string
    preClose: number
    minutes: MinuteData[]
}

/************************************************** */

/**
 * 分钟K线数据
 */
export interface MinuteKLineData {
    // 周期起始时间，格式yyyy-MM-dd HH:mm
    time: string
    open: number
    high: number
    low: number
    close: number
    // 成交量，单位手
    volume: number
    // 成交额，单位元
    turnover: number
}

/**
 * 分钟K线响应
 */
export interface MinuteKLineVO {
    code: string
    // 周期，取值1、5、15、30、60
    period: number
    hasMore: boolean
    kLine: MinuteKLineData[]
}

/************************************************** */

/**
 * 竞价盘口档位
 */
export interface AuctionItemVO {
    price: number
    // 数量，单位手
    volume: number
}

/**
 * 集合竞价快照
 */
export interface CallAuctionVO {
    code: string
    // 阶段，0休市1开盘竞价可撤单2开盘竞价不可撤单3开盘竞价冻结4连续交易5收盘竞价
    phase: number
    phaseName: string
    // 虚拟成交价
    indicativePrice: number | null
    // 虚拟可成交量，单位手
    matchedVolume: number
    // 未匹配买量，单位手
    unmatchedBuyVolume: number
    // 未匹配卖量，单位手
    unmatchedSellVolume: number
    bids: AuctionItemVO[]
    asks: AuctionItemVO[]
    // 快照时间，格式yyyy-MM-dd HH:mm:ss
    updateTime: string | null
}

/**
 * 集合竞价阶段枚举
 */
export const AuctionPhase = {
    Closed: 0,
    OpenBidding: 1,
    OpenBiddingLocked: 2,
    OpenFrozen: 3,
    Continuous: 4,
    CloseBidding: 5
} as const

/************************************************** */

/**
 * 委托信息
 */
export interface OrderVO {
    id: number
    accountId: number
    stockCode: string
    stockName: string
    price: number | null
    // 委托数量，单位股，买入为正卖出为负
    quantity: number
    // 累计已成交数量，单位股，非负
    soldQuantity: number
    // 委托类型，1限价2市价
    type: number
    // 委托状态，1未成交2部分成交3全部成交4已撤单
    status: number
    createTime: string
}

/**
 * 成交记录
 */
export interface TradeVO {
    id: number
    orderId: number
    accountId: number
    stockCode: string
    stockName: string
    price: number
    // 成交数量，单位股，买入为正卖出为负
    quantity: number
    // 成交金额，卖出为负
    amount: number
    createTime: string
}

/**
 * 持仓信息
 */
export interface PositionVO {
    id: number
    accountId: number
    code: string
    stockName: string
    // 持仓数量，单位股
    quantity: number
    // 可卖数量，单位股
    availableQuantity: number
    costPrice: number
    currentPrice: number | null
    marketValue: number | null
    profitLoss: number | null
    // 盈亏比例，0.1234表示盈利12.34%
    profitLossRatio: number | null
}

/**
 * 创建委托请求
 */
export interface CreateOrderDTO {
    accountId: number
    code: string
    // 委托类型，1限价2市价
    type: number
    price: number | null
    // 委托数量，单位股，买入为正卖出为负，绝对值为100的整数倍
    quantity: number
    // 幂等键
    orderKey: string
}

/**
 * 撤销委托请求
 */
export interface CancelOrderDTO {
    accountId: number
    orderId: number
}

/************************************************** */

/**
 * 委托类型枚举
 */
export const OrderType = { Limit: 1, Market: 2 } as const

/**
 * 委托状态枚举
 */
export const OrderStatus = {
    Pending: 1,
    Partial: 2,
    Filled: 3,
    Cancelled: 4
} as const

/**
 * 委托状态文本映射
 */
export const ORDER_STATUS_TEXT: Record<number, string> = {
    1: '未成交',
    2: '部分成交',
    3: '全部成交',
    4: '已撤单'
}

/**
 * 账户状态文本映射
 */
export const ACCOUNT_STATUS_TEXT: Record<number, string> = {
    1: '正常',
    2: '冻结'
}

/**
 * 排行指标文本映射
 */
export const SORT_BY_TEXT: Record<number, string> = {
    1: '价格',
    2: '涨跌幅',
    3: '成交量',
    4: '成交额',
    5: '换手率',
    6: '市净率',
    7: '总市值',
    8: '流通市值',
    9: '每股净资产'
}

/**
 * K线周期文本映射
 */
export const KLINE_PERIOD_TEXT: Record<number, string> = {
    1: '日K',
    2: '周K',
    3: '月K',
    4: '季K',
    5: '年K'
}

/**
 * 复权方式文本映射
 */
export const ADJUST_TEXT: Record<number, string> = {
    0: '不复权',
    1: '前复权',
    2: '后复权'
}
