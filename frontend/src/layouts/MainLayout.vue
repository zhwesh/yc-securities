<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import TradePasswordDialog from '@/components/TradePasswordDialog.vue'
import TradeReportToast from '@/components/TradeReportToast.vue'
import UserAvatar from '@/components/UserAvatar.vue'
import { brand } from '@/config/brand'
import { useAccountStore } from '@/stores/account'
import { useAuthStore } from '@/stores/auth'
import { useMarketStore } from '@/stores/market'
import { marketSocket, type WsStatus } from '@/ws/marketSocket'

const auth = useAuthStore()
const account = useAccountStore()
const market = useMarketStore()
const router = useRouter()
const route = useRoute()

// 顶部导航项
const navs = [
    { name: 'home', path: '/', label: '大盘' },
    { name: 'watchlist', path: '/watchlist', label: '自选' },
    { name: 'screener', path: '/screener', label: '选股' },
    { name: 'trade', path: '/trade', label: '持仓' },
    { name: 'assets', path: '/assets', label: '账号' }
]

// 当前激活的导航项
const activeName = computed(() => {
    const n = route.name as string
    if (n === 'stock-detail') return ''
    return n
})

// 行情连接状态
const wsStatus = ref<WsStatus>('idle')
// 取消状态监听函数
let offStatus: (() => void) | null = null

// 连接状态描述
const wsText = computed(() => {
    switch (wsStatus.value) {
        case 'open':
            return '行情已连接'
        case 'connecting':
            return '行情连接中'
        case 'closed':
            return '行情已断开，正在重连'
        default:
            return '行情未连接'
    }
})

onMounted(async () => {
    offStatus = marketSocket.onStatus((s) => (wsStatus.value = s))
    if (!account.loaded) {
        try {
            await account.loadAccounts()
        } catch {
            /* 请求异常由拦截器提示 */
        }
    }
    if (!market.watchlistLoaded) {
        await market.loadWatchlist()
        await market.loadWatchQuotes()
    }
})

onBeforeUnmount(() => {
    offStatus?.()
    offStatus = null
})

/**
 * 处理用户菜单命令
 * @param cmd 命令标识
 */
async function onCommand(cmd: string): Promise<void> {
    if (cmd === 'profile') {
        void router.push('/profile')
        return
    }
    if (cmd === 'assets') {
        void router.push('/assets')
        return
    }
    if (cmd === 'logout') {
        try {
            await ElMessageBox.confirm('确定要退出登录吗？', '退出登录', {
                confirmButtonText: '退出',
                cancelButtonText: '取消',
                type: 'warning'
            })
        } catch {
            return
        }
        auth.logout()
        account.reset()
        market.reset()
        void router.replace({ name: 'login' })
    }
}
</script>

<template>
    <div class="layout">
        <header class="header">
            <router-link to="/" class="brand">
                <img class="logo" :src="brand.logoUrl" :alt="brand.name" />
                <span class="brand-name">{{ brand.name }}</span>
            </router-link>

            <nav class="nav">
                <router-link
                    v-for="item in navs"
                    :key="item.name"
                    :to="item.path"
                    class="nav-item"
                    :class="{ active: activeName === item.name }"
                >
                    {{ item.label }}
                </router-link>
            </nav>

            <div class="header-right">
                <span class="ws" :title="wsText">
                    <span class="ws-dot" :class="`is-${wsStatus}`" />
                </span>

                <el-dropdown trigger="click" @command="onCommand">
                    <div class="user">
                        <span class="user-name">{{ auth.displayName }}</span>
                        <UserAvatar :src="auth.avatar" :name="auth.displayName" :size="30" />
                    </div>
                    <template #dropdown>
                        <el-dropdown-menu>
                            <el-dropdown-item command="profile">
                                <el-icon><User /></el-icon> 个人中心
                            </el-dropdown-item>
                            <el-dropdown-item command="assets">
                                <el-icon><Wallet /></el-icon> 资金账户
                            </el-dropdown-item>
                            <el-dropdown-item command="logout" divided>
                                <el-icon><SwitchButton /></el-icon> 退出登录
                            </el-dropdown-item>
                        </el-dropdown-menu>
                    </template>
                </el-dropdown>
            </div>
        </header>

        <main class="main">
            <router-view v-slot="{ Component }">
                <transition name="fade" mode="out-in">
                    <component :is="Component" />
                </transition>
            </router-view>
        </main>

        <TradePasswordDialog />
        <TradeReportToast />
    </div>
</template>

<style scoped>
.layout {
    display: flex;
    flex-direction: column;
    height: 100%;
    background: var(--bg-body);
}

.header {
    display: flex;
    align-items: center;
    gap: 24px;
    height: var(--header-h);
    flex: 0 0 var(--header-h);
    padding: 0 22px;
    background: var(--bg-header);
    border-bottom: 1px solid var(--border);
    z-index: 10;
}

.brand {
    display: flex;
    align-items: center;
    gap: 9px;
    flex: 0 0 auto;
}

.logo {
    display: block;
    flex: 0 0 auto;
    width: 34px;
    height: 34px;
    border-radius: 8px;
}

.brand-name {
    font-size: 17px;
    font-weight: 600;
    color: var(--text-primary);
    letter-spacing: 0.5px;
}

.nav {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    flex: 1 1 auto;
    height: 100%;
}

.nav-item {
    position: relative;
    display: flex;
    align-items: center;
    height: 100%;
    padding: 0 22px;
    font-size: 16px;
    color: var(--text-regular);
    transition: color 0.15s;
}

.nav-item:hover {
    color: var(--primary);
}

.nav-item.active {
    color: var(--primary);
    font-weight: 600;
}

.nav-item.active::after {
    content: '';
    position: absolute;
    left: 22px;
    right: 22px;
    bottom: 0;
    height: 2px;
    border-radius: 2px 2px 0 0;
    background: var(--primary);
}

.header-right {
    display: flex;
    align-items: center;
    gap: 14px;
    flex: 0 0 auto;
}

.ws {
    display: flex;
    align-items: center;
}

.ws-dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: var(--text-placeholder);
    transition: background 0.2s;
}

.ws-dot.is-open {
    background: var(--down);
}

.ws-dot.is-connecting {
    background: #f5a524;
    animation: pulse 1.1s ease-in-out infinite;
}

.ws-dot.is-closed {
    background: var(--up);
    animation: pulse 1.1s ease-in-out infinite;
}

@keyframes pulse {
    0%,
    100% {
        opacity: 1;
    }
    50% {
        opacity: 0.3;
    }
}

.user {
    display: flex;
    align-items: center;
    gap: 8px;
    height: 38px;
    padding: 0 6px;
    border-radius: 4px;
    cursor: pointer;
    outline: none;
    transition: background 0.15s;
}

.user:hover {
    background: var(--bg-hover);
}

.user-name {
    font-size: 15px;
    color: var(--text-regular);
    max-width: 100px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.main {
    flex: 1 1 auto;
    min-height: 0;
    padding: 10px;
    overflow: auto;
}
</style>
