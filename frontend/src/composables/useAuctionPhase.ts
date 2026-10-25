import { computed, onScopeDispose, ref, watch, type Ref } from 'vue'
import { marketApi } from '@/api/market'
import { AuctionPhase } from '@/types'

/**
 * 集合竞价阶段状态
 * 竞价阶段仅接受限价委托，09:20至09:30与14:57至15:00不可撤单
 * @param code 股票代码来源
 * @returns 阶段状态与撤单判断
 */
export function useAuctionPhase(code: Ref<string | undefined> | (() => string | undefined)) {
    // 读取股票代码
    const getCode = typeof code === 'function' ? code : () => code.value

    // 竞价阶段编号
    const phase = ref(0)
    // 竞价阶段名称
    const phaseName = ref('')
    // 轮询定时器句柄
    let timer: number | null = null
    // 组件销毁标记
    let disposed = false

    // 是否处于集合竞价阶段
    const isAuction = computed(
        () =>
            phase.value === AuctionPhase.OpenBidding ||
            phase.value === AuctionPhase.OpenBiddingLocked ||
            phase.value === AuctionPhase.OpenFrozen ||
            phase.value === AuctionPhase.CloseBidding
    )

    // 是否仅接受限价委托
    const limitOnly = computed(() => isAuction.value)

    // 当前阶段是否允许撤单
    const cancelAllowed = computed(
        () =>
            phase.value !== AuctionPhase.OpenBiddingLocked &&
            phase.value !== AuctionPhase.OpenFrozen &&
            phase.value !== AuctionPhase.CloseBidding
    )

    // 不允许撤单时的提示文本
    const cancelBlockReason = computed(() => {
        if (
            phase.value === AuctionPhase.OpenBiddingLocked ||
            phase.value === AuctionPhase.OpenFrozen
        ) {
            return '开盘集合竞价09:20–09:30不可撤单'
        }
        if (phase.value === AuctionPhase.CloseBidding) {
            return '收盘集合竞价14:57–15:00不可撤单'
        }
        return ''
    })

    /**
     * 拉取竞价快照并安排下一次轮询
     */
    async function refresh(): Promise<void> {
        if (disposed) return
        const curCode = getCode()
        if (!curCode) {
            schedule()
            return
        }
        try {
            const snap = await marketApi.auction(curCode)
            phase.value = snap.phase ?? 0
            phaseName.value = snap.phaseName ?? ''
        } catch {
            /* 请求失败时按非竞价阶段处理 */
        }
        schedule()
    }

    /**
     * 安排下一次轮询，竞价阶段间隔2秒，其余阶段间隔15秒
     */
    function schedule(): void {
        if (disposed) return
        if (timer !== null) window.clearTimeout(timer)
        timer = window.setTimeout(refresh, isAuction.value ? 2000 : 15000)
    }

    watch(getCode, () => void refresh(), { immediate: true })

    onScopeDispose(() => {
        disposed = true
        if (timer !== null) window.clearTimeout(timer)
        timer = null
    })

    return {
        phase,
        phaseName,
        isAuction,
        limitOnly,
        cancelAllowed,
        cancelBlockReason,
        refresh
    }
}
