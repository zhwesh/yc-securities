import { ColorType, CrosshairMode, type DeepPartial, type ChartOptions } from 'lightweight-charts'

/**
 * 图表配色
 */
export const CHART_COLORS = {
    up: '#e5484d',
    down: '#00a870',
    upFill: 'rgba(229, 72, 77, 0.55)',
    downFill: 'rgba(0, 168, 112, 0.55)',
    line: '#e5484d',
    avg: '#f5a524',
    grid: '#f2f4f7',
    border: '#ebedf0',
    text: '#86909c',
    crosshair: '#a8adb7',
    ma5: '#f5a524',
    ma10: '#3b82f6',
    ma20: '#a855f7'
}

/**
 * 生成图表基础配置
 * @param width 图表宽度
 * @param height 图表高度
 * @returns 图表配置对象
 */
export function baseChartOptions(width: number, height: number): DeepPartial<ChartOptions> {
    return {
        width,
        height,
        layout: {
            background: { type: ColorType.Solid, color: '#ffffff' },
            textColor: CHART_COLORS.text,
            fontSize: 11,
            // 关闭图表内置水印，署名由页面文字链接提供
            attributionLogo: false,
            fontFamily:
                "-apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif"
        },
        grid: {
            vertLines: { color: CHART_COLORS.grid },
            horzLines: { color: CHART_COLORS.grid }
        },
        rightPriceScale: {
            borderColor: CHART_COLORS.border,
            scaleMargins: { top: 0.08, bottom: 0.26 }
        },
        timeScale: {
            borderColor: CHART_COLORS.border,
            rightOffset: 2,
            minBarSpacing: 1.5
        },
        crosshair: {
            mode: CrosshairMode.Normal,
            vertLine: {
                color: CHART_COLORS.crosshair,
                width: 1,
                style: 3,
                labelBackgroundColor: '#4e5969'
            },
            horzLine: {
                color: CHART_COLORS.crosshair,
                width: 1,
                style: 3,
                labelBackgroundColor: '#4e5969'
            }
        },
        handleScroll: { mouseWheel: true, pressedMouseMove: true },
        handleScale: { mouseWheel: true, pinch: true, axisPressedMouseMove: true }
    }
}

/**
 * 计算简单移动平均
 * @param values 数值序列
 * @param period 周期长度
 * @returns 与输入等长的序列，前period-1项为null
 */
export function sma(values: number[], period: number): (number | null)[] {
    const out: (number | null)[] = new Array(values.length).fill(null)
    let sum = 0
    for (let i = 0; i < values.length; i += 1) {
        sum += values[i]
        if (i >= period) sum -= values[i - period]
        if (i >= period - 1) out[i] = sum / period
    }
    return out
}
