import { computed, onScopeDispose, ref } from 'vue'

/**
 * 短信验证码倒计时
 * @param duration 倒计时总秒数
 * @returns 剩余秒数、运行状态与启停方法
 */
export function useSmsCountdown(duration = 60) {
    // 剩余秒数
    const seconds = ref(0)
    // 计时器句柄
    let timer: number | null = null

    // 倒计时是否进行中
    const running = computed(() => seconds.value > 0)

    /**
     * 停止倒计时并清除计时器
     */
    function stop(): void {
        if (timer !== null) {
            window.clearInterval(timer)
            timer = null
        }
    }

    /**
     * 启动倒计时，重复调用时重置剩余秒数
     */
    function start(): void {
        stop()
        seconds.value = duration
        timer = window.setInterval(() => {
            seconds.value -= 1
            if (seconds.value <= 0) stop()
        }, 1000)
    }

    onScopeDispose(stop)

    return { seconds, running, start, stop }
}
