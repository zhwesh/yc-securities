<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
    AreaSeries,
    CandlestickSeries,
    CrosshairMode,
    HistogramSeries,
    LineSeries,
    LineStyle,
    createChart,
    type AreaData,
    type CandlestickData,
    type HistogramData,
    type IChartApi,
    type ISeriesApi,
    type LineData,
    type LogicalRange,
    type Time,
    type UTCTimestamp
} from 'lightweight-charts'
import { marketApi } from '@/api/market'
import type { StockQuoteVO } from '@/types'
import { CHART_COLORS, baseChartOptions, sma } from '@/utils/chart'
import { marketSocket } from '@/ws/marketSocket'

const props = defineProps<{
    code: string
    /** minute | mk1 | mk5 | mk15 | mk30 | mk60 | dk1 | dk2 | dk3 | dk4 | dk5 */
    tab: string
    adjust: number
}>()

type Mode = 'minute' | 'minuteK' | 'minuteK120' | 'dayK'

interface Bar {
    time: Time
    raw: string
    dateKey: string
    open: number
    high: number
    low: number
    close: number
    volume: number
    turnover: number
    avg?: number
}

const mainEl = ref<HTMLDivElement | null>(null)
const subEl = ref<HTMLDivElement | null>(null)

const loading = ref(false)
const loadingMore = ref(false)
const error = ref('')
const empty = ref(false)
const hasMore = ref(false)
const hasData = ref(false)
const subMetric = ref<'volume' | 'turnover'>('volume')
const preClose = ref<number | null>(null)

function modeOf(tab: string): Mode {
    if (tab === 'minute') return 'minute'
    if (tab === 'mk120') return 'minuteK120'
    return tab.startsWith('mk') ? 'minuteK' : 'dayK'
}

const mode = computed<Mode>(() => modeOf(props.tab))

/** 主图形态：分时用面积图，其余用蜡烛图。只有形态变化才需要重建图表 */
const seriesKind = computed<'area' | 'candle'>(() => (mode.value === 'minute' ? 'area' : 'candle'))

/** 是否分钟级 K 线（120分也算） */
const isMinuteK = computed(() => mode.value === 'minuteK' || mode.value === 'minuteK120')

const period = computed(() => {
    // 120分钟后端没有，用60分钟两两合并
    if (mode.value === 'minuteK120') return 60
    return Number(props.tab.replace(/^(mk|dk)/, '')) || 1
})

const subMetricOptions = computed(() =>
    mode.value === 'minute'
        ? [{ value: 'volume', label: '分时成交量' }]
        : [
                { value: 'volume', label: '成交量' },
                { value: 'turnover', label: '成交额' }
            ]
)

/* ------------------------------- 图表实例 ------------------------------- */

let mainChart: IChartApi | null = null
let subChart: IChartApi | null = null
let candleSeries: ISeriesApi<'Candlestick'> | null = null
let areaSeries: ISeriesApi<'Area'> | null = null
let avgSeries: ISeriesApi<'Line'> | null = null
let maLines: ISeriesApi<'Line'>[] = []
let volSeries: ISeriesApi<'Histogram'> | null = null
let mainRo: ResizeObserver | null = null
let subRo: ResizeObserver | null = null
let offSub: (() => void) | null = null
let syncing = false
let loadingMoreLock = false

const MA_PERIODS = [5, 10, 20] as const

let bars: Bar[] = []
/** 当前分钟 K 是不是用分时数据现场合成的（合成时没有更早的数据可翻） */
let derivedFromMinute = false

/* ------------------------------ 工具函数 ------------------------------ */

function parseMinuteTs(s: string): UTCTimestamp {
    const [datePart, timePart = '00:00'] = s.split(' ')
    const [y, mo, da] = datePart.split('-').map(Number)
    const [h, mi] = timePart.split(':').map(Number)
    return (Date.UTC(y, mo - 1, da, h, mi) / 1000) as UTCTimestamp
}

function todayKey(): string {
    const d = new Date()
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(
        d.getDate()
    ).padStart(2, '0')}`
}

function hmOf(ts: UTCTimestamp): string {
    const d = new Date(Number(ts) * 1000)
    return `${String(d.getUTCHours()).padStart(2, '0')}:${String(d.getUTCMinutes()).padStart(2, '0')}`
}

function toCandle(b: Bar): CandlestickData<Time> {
    return { time: b.time, open: b.open, high: b.high, low: b.low, close: b.close }
}

/** 把60分钟 K 按「上午 / 下午」各自合并成一根120分钟 K */
function aggregate120(list: Bar[]): Bar[] {
    const out: Bar[] = []
    let cur: Bar | null = null
    let curKey = ''
    for (const b of list) {
        const hour = Number(b.raw.slice(11, 13))
        const key = `${b.dateKey}-${hour < 12 ? 'am' : 'pm'}`
        if (!cur || key !== curKey) {
            if (cur) out.push(cur)
            cur = { ...b }
            curKey = key
        } else {
            cur.high = Math.max(cur.high, b.high)
            cur.low = Math.min(cur.low, b.low)
            cur.close = b.close
            cur.volume += b.volume
            cur.turnover += b.turnover
        }
    }
    if (cur) out.push(cur)
    return out
}

function toAreaPoint(b: Bar): AreaData<Time> {
    return { time: b.time, value: b.close }
}

function toAvgPoint(b: Bar): LineData<Time> {
    return { time: b.time, value: b.avg ?? b.close }
}

function maSeriesData(periodN: number): LineData<Time>[] {
    const values = sma(
        bars.map((b) => b.close),
        periodN
    )
    const out: LineData<Time>[] = []
    for (let i = 0; i < bars.length; i += 1) {
        const v = values[i]
        if (v != null) out.push({ time: bars[i].time, value: Number(v.toFixed(3)) })
    }
    return out
}

function subSeriesData(): HistogramData<Time>[] {
    const out: HistogramData<Time>[] = []
    let prev = preClose.value ?? 0
    for (const b of bars) {
        const up = mode.value === 'minute' ? b.close >= (prev || b.close) : b.close >= b.open
        out.push({
            time: b.time,
            value: subMetric.value === 'volume' ? b.volume : b.turnover,
            color: up ? CHART_COLORS.upFill : CHART_COLORS.downFill
        })
        prev = b.close
    }
    return out
}

/* ------------------------------- 渲染 ------------------------------- */

function render(resetView: boolean): void {
    const avgData = mode.value === 'minute'

    if (areaSeries) {
        areaSeries.setData(avgData ? bars.map(toAreaPoint) : [])
    }
    if (avgSeries) {
        avgSeries.setData(avgData ? bars.map(toAvgPoint) : [])
    }
    if (candleSeries) {
        candleSeries.setData(avgData ? [] : bars.map(toCandle))
    }
    maLines.forEach((line, i) => {
        line.setData(avgData ? [] : maSeriesData(MA_PERIODS[i]))
    })
    volSeries?.setData(subSeriesData())

    if (!resetView) return
    const n = bars.length
    if (n === 0) return
    if (mode.value === 'minute') {
        mainChart?.timeScale().fitContent()
        subChart?.timeScale().fitContent()
    } else if (n > 120) {
        const range: { from: number; to: number } = { from: n - 120, to: n + 2 }
        mainChart?.timeScale().setVisibleLogicalRange(range)
        subChart?.timeScale().setVisibleLogicalRange(range)
    } else {
        mainChart?.timeScale().fitContent()
        subChart?.timeScale().fitContent()
    }
}

/* ------------------------------- 加载 ------------------------------- */

async function loadMinute(): Promise<void> {
    const res = await marketApi.minute(props.code)
    preClose.value = res.preClose ?? null
    subMetric.value = 'volume'
    bars = (res.minutes ?? []).map((m) => {
        const ts = m.time as unknown as UTCTimestamp
        return {
            time: ts as Time,
            raw: String(m.time),
            dateKey: todayKey(),
            open: m.price,
            high: m.price,
            low: m.price,
            close: m.price,
            volume: m.volume,
            turnover: 0,
            avg: m.avgPrice
        }
    })
    hasMore.value = false
}

function mapMinuteK(rows: { time: string; open: number; high: number; low: number; close: number; volume: number; turnover: number }[]): Bar[] {
    const list: Bar[] = rows.map((k) => ({
        time: parseMinuteTs(k.time) as Time,
        raw: k.time,
        dateKey: k.time.slice(0, 10),
        open: k.open,
        high: k.high,
        low: k.low,
        close: k.close,
        volume: k.volume,
        turnover: k.turnover
    }))
    return mode.value === 'minuteK120' ? aggregate120(list) : list
}

async function loadMinuteK(): Promise<void> {
    const res = await marketApi.minuteKline({ code: props.code, period: period.value, limit: 240 })
    const list = res.kLine ?? []

    if (list.length) {
        derivedFromMinute = false
        bars = mapMinuteK(list)
        hasMore.value = res.hasMore
        return
    }

    // 分钟K无数据时由分时数据合成，仅覆盖当日且不支持向前翻页
    derivedFromMinute = true
    bars = await buildBarsFromMinute(period.value, mode.value === 'minuteK120')
    hasMore.value = false
}

/**
 * 计算分钟点所属K线桶的起始分钟序号
 * 交易时段按开盘时间对齐，非交易时段按自然时钟对齐
 * @param clock 当天第几分钟
 * @param span 桶跨度，单位分钟
 * @returns 桶起始的分钟序号
*/
function bucketStartClock(clock: number, span: number): number {
    const lastIndex = Math.max(0, Math.ceil(120 / span) - 1)
    const alignFrom = (start: number) =>
        start + Math.min(Math.floor((clock - start) / span), lastIndex) * span

    if (clock >= 570 && clock <= 690) return alignFrom(570) // 09:30–11:30
    if (clock >= 780 && clock <= 900) return alignFrom(780) // 13:00–15:00
    return Math.floor(clock / span) * span
}

/**
 * 由分时数据合成N分钟K线
 * 开高低收取自区间内首末与极值，成交量求和，成交额按价乘量估算
 * @param spanMin 周期，单位分钟
 * @param mergeHalfDay 是否合并为半日周期
 * @returns K线数组
*/
async function buildBarsFromMinute(spanMin: number, mergeHalfDay: boolean): Promise<Bar[]> {
    const res = await marketApi.minute(props.code)
    const points = res.minutes ?? []
    if (!points.length) return []

    const span = mergeHalfDay ? 120 : spanMin
    const buckets = new Map<number, Bar>()
    const day = todayKey()

    for (const m of points) {
        const d = new Date(m.time * 1000)
        const y = d.getUTCFullYear()
        const mo = d.getUTCMonth()
        const da = d.getUTCDate()
        const clock = d.getUTCHours() * 60 + d.getUTCMinutes()

        const startClock = bucketStartClock(clock, span)
        const ts = Date.UTC(y, mo, da, Math.floor(startClock / 60), startClock % 60) / 1000

        const hh = String(Math.floor(startClock / 60)).padStart(2, '0')
        const mm = String(startClock % 60).padStart(2, '0')

        const exist = buckets.get(ts)
        if (!exist) {
            buckets.set(ts, {
                time: ts as unknown as Time,
                raw: `${y}-${String(mo + 1).padStart(2, '0')}-${String(da).padStart(2, '0')} ${hh}:${mm}`,
                dateKey: day,
                open: m.price,
                high: m.price,
                low: m.price,
                close: m.price,
                volume: m.volume,
                turnover: m.price * m.volume * 100
            })
        } else {
            exist.high = Math.max(exist.high, m.price)
            exist.low = Math.min(exist.low, m.price)
            exist.close = m.price
            exist.volume += m.volume
            exist.turnover += m.price * m.volume * 100
        }
    }

    return [...buckets.keys()].sort((a, b) => a - b).map((k) => buckets.get(k)!)
}

async function loadDayK(): Promise<void> {
    const res = await marketApi.kline({
        code: props.code,
        period: period.value,
        adjust: props.adjust,
        limit: 300
    })
    bars = (res.kLine ?? []).map((k) => ({
        time: k.time as Time,
        raw: k.time,
        dateKey: k.time,
        open: k.open,
        high: k.high,
        low: k.low,
        close: k.close,
        volume: k.volume,
        turnover: k.turnover
    }))
    hasMore.value = res.hasMore
}

async function load(): Promise<void> {
    if (!props.code) return
    loading.value = true
    error.value = ''
    try {
        derivedFromMinute = false
        if (mode.value === 'minute') await loadMinute()
        else if (isMinuteK.value) await loadMinuteK()
        else await loadDayK()
        empty.value = bars.length === 0
        hasData.value = bars.length > 0
        render(true)
    } catch (e) {
        bars = []
        empty.value = false
        hasData.value = false
        error.value = e instanceof Error ? e.message : '图表数据加载失败'
        render(true)
    } finally {
        loading.value = false
    }
}

/** 向左拖到边缘时按游标加载更早的数据 */
async function loadMore(): Promise<void> {
    if (loadingMoreLock || !hasMore.value || bars.length === 0) return
    if (mode.value === 'minute') return
    if (derivedFromMinute) return
    loadingMoreLock = true
    loadingMore.value = true
    const before = bars.length
    const range = mainChart?.timeScale().getVisibleLogicalRange() ?? null
    try {
        let earlier: Bar[] = []
        let more = false
        if (isMinuteK.value) {
            const res = await marketApi.minuteKline({
                code: props.code,
                period: period.value,
                limit: 240,
                endTime: `${bars[0].raw}:00`
            })
            earlier = mapMinuteK(res.kLine ?? [])
            more = res.hasMore
        } else {
            const res = await marketApi.kline({
                code: props.code,
                period: period.value,
                adjust: props.adjust,
                limit: 300,
                endDate: bars[0].raw
            })
            earlier = (res.kLine ?? []).map((k) => ({
                time: k.time as Time,
                raw: k.time,
                dateKey: k.time,
                open: k.open,
                high: k.high,
                low: k.low,
                close: k.close,
                volume: k.volume,
                turnover: k.turnover
            }))
            more = res.hasMore
        }

        if (!earlier.length) {
            hasMore.value = false
            return
        }
        bars = [...earlier, ...bars]
        hasMore.value = more
        render(false)
        const added = bars.length - before
        if (range) {
            const shifted: { from: number; to: number } = {
                from: range.from + added,
                to: range.to + added
            }
            mainChart?.timeScale().setVisibleLogicalRange(shifted)
            subChart?.timeScale().setVisibleLogicalRange(shifted)
        }
    } catch {
        hasMore.value = false
    } finally {
        loadingMoreLock = false
        loadingMore.value = false
    }
}

/* --------------------------- 实时：更新最后一根 --------------------------- */

function onQuote(q: StockQuoteVO): void {
    const price = Number(q.price)
    if (!Number.isFinite(price) || price <= 0) return
    if (preClose.value == null && q.preClose) preClose.value = q.preClose
    if (!bars.length) return
    const last = bars[bars.length - 1]
    if (last.dateKey !== todayKey()) return

    last.close = price
    last.high = Math.max(last.high, price)
    last.low = Math.min(last.low, price)
    if (mode.value === 'minute') {
        last.avg = last.avg ?? price
        areaSeries?.update(toAreaPoint(last))
        avgSeries?.update(toAvgPoint(last))
    } else {
        if (mode.value === 'dayK' && period.value === 1 && typeof q.volume === 'number') {
            last.volume = q.volume
        }
        candleSeries?.update(toCandle(last))
        if (maLines.length) {
            const closes = bars.map((b) => b.close)
            MA_PERIODS.forEach((p, i) => {
                const values = sma(closes, p)
                const v = values[values.length - 1]
                const line = maLines[i]
                if (line && v != null) line.update({ time: last.time, value: Number(v.toFixed(3)) })
            })
        }
    }
    const sub = subSeriesData()
    const lastSub = sub[sub.length - 1]
    if (lastSub) volSeries?.update(lastSub)
}

function subscribe(): void {
    offSub?.()
    offSub = marketSocket.subscribe([props.code], { quote: onQuote })
}

/* ------------------------------ 建图 / 拆图 ------------------------------ */

function chartOptions(width: number, height: number, showTimeAxis: boolean) {
    const opt = baseChartOptions(width, height)
    const isIntraday = mode.value !== 'dayK'
    opt.timeScale = {
        borderColor: CHART_COLORS.border,
        rightOffset: 0,
        // 两端都锁住：划到最早/最新一根就停住，不会再拖出空白
        fixLeftEdge: true,
        fixRightEdge: true,
        visible: showTimeAxis,
        timeVisible: isIntraday,
        secondsVisible: false
    }
    opt.crosshair = {
        mode: CrosshairMode.Normal,
        vertLine: { color: CHART_COLORS.crosshair, width: 1, style: LineStyle.Dashed },
        horzLine: { color: CHART_COLORS.crosshair, width: 1, style: LineStyle.Dashed }
    }
    opt.localization = {
        locale: 'zh-CN',
        timeFormatter: (time: Time) => {
            if (mode.value === 'minute' || mode.value === 'minuteK') return hmOf(time as UTCTimestamp)
            return String(time)
        }
    }
    return opt
}

function buildCharts(): void {
    const main = mainEl.value
    const sub = subEl.value
    if (!main || !sub) return

    const mw = main.clientWidth
    const mh = main.clientHeight
    const sw = sub.clientWidth
    const sh = sub.clientHeight

    mainChart = createChart(main, chartOptions(mw, mh, false))
    subChart = createChart(sub, chartOptions(sw, sh, true))

    mainChart.priceScale('right').applyOptions({ scaleMargins: { top: 0.08, bottom: 0.06 } })
    subChart.priceScale('right').applyOptions({ scaleMargins: { top: 0.12, bottom: 0.02 } })

    if (mode.value === 'minute') {
        areaSeries = mainChart.addSeries(AreaSeries, {
            lineWidth: 1,
            priceLineVisible: false,
            lastValueVisible: false,
            priceFormat: { type: 'price', precision: 2, minMove: 0.01 },
            crosshairMarkerRadius: 3,
            lineColor: CHART_COLORS.up,
            topColor: 'rgba(229, 72, 77, 0.18)',
            bottomColor: 'rgba(229, 72, 77, 0.01)'
        })
        avgSeries = mainChart.addSeries(LineSeries, {
            color: CHART_COLORS.avg,
            lineWidth: 1,
            priceLineVisible: false,
            lastValueVisible: false,
            crosshairMarkerVisible: false
        })
    } else {
        candleSeries = mainChart.addSeries(CandlestickSeries, {
            upColor: CHART_COLORS.up,
            downColor: CHART_COLORS.down,
            borderUpColor: CHART_COLORS.up,
            borderDownColor: CHART_COLORS.down,
            wickUpColor: CHART_COLORS.up,
            wickDownColor: CHART_COLORS.down,
            priceFormat: { type: 'price', precision: 2, minMove: 0.01 }
        })
        maLines = MA_PERIODS.map((p, i) =>
            mainChart!.addSeries(LineSeries, {
                color: [CHART_COLORS.ma5, CHART_COLORS.ma10, CHART_COLORS.ma20][i],
                lineWidth: 1,
                priceLineVisible: false,
                lastValueVisible: false,
                crosshairMarkerVisible: false
            })
        )
    }

    volSeries = subChart.addSeries(HistogramSeries, {
        priceFormat: { type: 'volume' },
        priceLineVisible: false,
        lastValueVisible: false
    })

    // 主图 / 副图时间轴联动
    const link = (from: IChartApi, to: IChartApi) => (range: LogicalRange | null) => {
        if (!range || syncing) return
        syncing = true
        to.timeScale().setVisibleLogicalRange(range)
        syncing = false
    }
    mainChart.timeScale().subscribeVisibleLogicalRangeChange(link(mainChart, subChart))
    subChart.timeScale().subscribeVisibleLogicalRangeChange(link(subChart, mainChart))

    mainChart.timeScale().subscribeVisibleLogicalRangeChange((range) => {
        if (range && range.from < 3) void loadMore()
    })

    if (mode.value === 'minute' && preClose.value != null && preClose.value > 0) {
        areaSeries?.createPriceLine({
            price: preClose.value,
            color: '#a8adb1',
            lineWidth: 1,
            lineStyle: LineStyle.Dashed,
            axisLabelVisible: true,
            title: '昨收'
        })
    }

    mainRo = new ResizeObserver(() => {
        if (!mainChart || !main) return
        mainChart.applyOptions({ width: main.clientWidth, height: main.clientHeight })
    })
    mainRo.observe(main)

    subRo = new ResizeObserver(() => {
        if (!subChart || !sub) return
        subChart.applyOptions({ width: sub.clientWidth, height: sub.clientHeight })
    })
    subRo.observe(sub)
}

function destroyCharts(): void {
    mainRo?.disconnect()
    subRo?.disconnect()
    mainRo = null
    subRo = null
    mainChart?.remove()
    subChart?.remove()
    mainChart = null
    subChart = null
    candleSeries = null
    areaSeries = null
    avgSeries = null
    maLines = []
    volSeries = null
}

/* ------------------------------- 生命周期 ------------------------------- */

onMounted(async () => {
    buildCharts()
    await load()
    subscribe()
})

onBeforeUnmount(() => {
    offSub?.()
    offSub = null
    destroyCharts()
})

watch(
    () => props.tab,
    async (_next, prev) => {
        const prevKind = modeOf(prev ?? '') === 'minute' ? 'area' : 'candle'
        if (prevKind !== seriesKind.value) {
            destroyCharts()
            await load()
            buildCharts()
            render(true)
        } else {
            await load()
        }
        subscribe()
    }
)

watch(
    () => props.adjust,
    async () => {
        if (mode.value !== 'dayK') return
        await load()
    }
)

watch(
    () => props.code,
    async () => {
        await load()
        subscribe()
    }
)

watch(subMetric, () => {
    volSeries?.setData(subSeriesData())
})
</script>

<template>
    <div class="stock-chart">
        <div class="toolbar">
            <span v-if="mode === 'minute'" class="legend">
                <i class="dot up" />价格
                <i class="dot avg" />均价
            </span>
            <span v-else class="legend ma">
                <i v-for="(p, i) in MA_PERIODS" :key="p" class="ma-item">
                    <i class="line" :class="`ma${i}`" />MA{{ p }}
                </i>
            </span>

            <span class="spacer" />

            <span v-if="loading" class="hint">加载中…</span>
            <span v-else-if="loadingMore" class="hint">加载更早数据…</span>
            <span v-else-if="error" class="hint err">{{ error }}</span>
            <span v-else-if="hasMore" class="hint">向左拖动加载更早数据</span>

            <!-- Lightweight Charts 署名链接 -->
            <a
                class="tv"
                href="https://www.tradingview.com/"
                target="_blank"
                rel="noopener noreferrer"
                title="图表库：TradingView Lightweight Charts"
            >
                TradingView
            </a>
        </div>

        <div ref="mainEl" v-loading="loading" class="main-pane" />
        <div class="sub-head">
            <el-select v-model="subMetric" size="small" class="metric-select">
                <el-option
                    v-for="opt in subMetricOptions"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                />
            </el-select>
        </div>
        <div ref="subEl" class="sub-pane" />

        <div v-if="empty && !loading" class="overlay">
            {{ mode === 'minuteK' || mode === 'minuteK120' ? '该周期暂无 K 线数据' : '暂无数据' }}
        </div>
    </div>
</template>

<style scoped>
.stock-chart {
    position: relative;
    display: flex;
    flex-direction: column;
    height: 100%;
    min-height: 0;
}

.toolbar {
    display: flex;
    align-items: center;
    gap: 14px;
    height: 26px;
    padding: 0 10px;
    font-size: 13px;
    color: var(--text-secondary);
    flex: 0 0 auto;
}

.legend {
    display: inline-flex;
    align-items: center;
    gap: 6px;
}

.dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    display: inline-block;
}

.dot.up {
    background: var(--up);
}

.dot.avg {
    background: var(--el-color-warning, #f5a524);
}

.menta {
    gap: 10px;
}

.ma-item {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    margin-right: 10px;
    font-style: normal;
}

.line {
    width: 12px;
    height: 2px;
    border-radius: 1px;
    display: inline-block;
}

.line.ma0 {
    background: #f5a524;
}

.line.ma1 {
    background: #3b82f6;
}

.line.ma2 {
    background: #a855f7;
}

.spacer {
    flex: 1 1 auto;
}

.hint {
    font-size: 13px;
    color: var(--text-placeholder);
}

.hint.err {
    color: var(--up);
}

.tv {
    margin-left: 10px;
    font-size: 12px;
    color: var(--text-placeholder);
    text-decoration: none;
    opacity: 0.7;
}

.tv:hover {
    color: var(--text-secondary);
    text-decoration: underline;
}

.main-pane {
    flex: 1 1 auto;
    min-height: 0;
}

.sub-head {
    flex: 0 0 auto;
    padding: 2px 10px 0;
    border-top: 1px solid var(--divider);
}

.metric-select {
    width: 118px;
}

.metric-select :deep(.el-select__wrapper) {
    box-shadow: none;
    padding-left: 0;
}

.sub-pane {
    flex: 0 0 128px;
    min-height: 0;
}

.overlay {
    position: absolute;
    left: 0;
    right: 0;
    top: 46%;
    text-align: center;
    font-size: 15px;
    color: var(--text-placeholder);
    pointer-events: none;
}
</style>
