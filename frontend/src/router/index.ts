import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { onUnauthorized } from '@/utils/authToken'
import { useAuthStore } from '@/stores/auth'

// 路由表
const routes: RouteRecordRaw[] = [
    {
        path: '/login',
        name: 'login',
        component: () => import('@/views/Login.vue'),
        meta: { public: true, title: '登录' }
    },
    {
        path: '/register',
        name: 'register',
        component: () => import('@/views/Register.vue'),
        meta: { public: true, title: '注册' }
    },
    {
        path: '/reset-password',
        name: 'reset-password',
        component: () => import('@/views/ResetPassword.vue'),
        meta: { public: true, title: '找回密码' }
    },
    {
        path: '/',
        component: () => import('@/layouts/MainLayout.vue'),
        children: [
            {
                path: '',
                name: 'home',
                component: () => import('@/views/Home.vue'),
                meta: { title: '大盘' }
            },
            {
                path: 'watchlist',
                name: 'watchlist',
                component: () => import('@/views/Watchlist.vue'),
                meta: { title: '自选' }
            },
            {
                path: 'screener',
                name: 'screener',
                component: () => import('@/views/Screener.vue'),
                meta: { title: '选股' }
            },
            {
                path: 'stock/:code',
                name: 'stock-detail',
                component: () => import('@/views/StockDetail.vue'),
                meta: { title: '个股详情' }
            },
            {
                path: 'trade',
                name: 'trade',
                component: () => import('@/views/Trade.vue'),
                meta: { title: '持仓' }
            },
            {
                path: 'assets',
                name: 'assets',
                component: () => import('@/views/Assets.vue'),
                meta: { title: '资金' }
            },
            {
                path: 'profile',
                name: 'profile',
                component: () => import('@/views/Profile.vue'),
                meta: { title: '个人中心' }
            }
        ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
    history: createWebHistory(),
    routes,
    scrollBehavior: () => ({ top: 0 })
})

/**
 * 全局前置守卫，校验登录状态并恢复用户信息
 * @param to 目标路由
 * @returns 放行时返回true，需要重定向时返回跳转地址
 */
router.beforeEach(async (to) => {
    const auth = useAuthStore()

    if (to.meta.public) {
        if (auth.isLoggedIn && (to.name === 'login' || to.name === 'register')) {
            return { path: '/' }
        }
        return true
    }

    if (!auth.isLoggedIn) {
        return { name: 'login', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } }
    }

    if (!auth.user) {
        const ok = await auth.fetchProfile()
        if (!ok) {
            return { name: 'login', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } }
        }
    }

    return true
})

// 设置页面标题
router.afterEach((to) => {
    const title = (to.meta.title as string | undefined) ?? ''
    document.title = title ? `${title} · 易财证券` : '易财证券'
})

// 登录状态失效时清除状态并跳转登录页
onUnauthorized(() => {
    const auth = useAuthStore()
    auth.handleExpired()
    const current = router.currentRoute.value
    if (current.name !== 'login') {
        void router.replace({
            name: 'login',
            query: current.fullPath === '/' ? {} : { redirect: current.fullPath }
        })
    }
})

export default router
