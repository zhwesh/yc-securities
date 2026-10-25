<script setup lang="ts">
import { brand } from '@/config/brand'

defineProps<{
    title: string
    subtitle?: string
}>()
</script>

<template>
    <div class="auth">
        <section class="brand-side">
            <div class="brand-top">
                <span class="logo"><img :src="brand.logoUrl" :alt="brand.name" /></span>
                <div class="brand-text">
                    <div class="brand-name">{{ brand.name }}</div>
                    <div class="brand-sub">{{ brand.subName }}</div>
                </div>
            </div>

            <div class="brand-main">
                <h2 class="slogan">专业A股行情与交易终端</h2>
                <p class="slogan-sub">实时行情·五档盘口·多周期K线·限价市价委托</p>
                <ul class="feature-list">
                    <li><span class="dot" />实时行情与逐笔成交，秒级推送</li>
                    <li><span class="dot" />日/周/月/季/年K线，支持前复权与后复权</li>
                    <li><span class="dot" />资金账户、持仓盈亏与T+1可卖数量一目了然</li>
                </ul>
            </div>

            <div class="brand-foot">
                <span>{{ brand.copyright }}</span>
            </div>

            <div class="grid-bg" aria-hidden="true" />
            <div class="glow glow-1" aria-hidden="true" />
            <div class="glow glow-2" aria-hidden="true" />
        </section>

        <section class="form-side">
            <div class="form-card">
                <h1 class="title">{{ title }}</h1>
                <p v-if="subtitle" class="subtitle">{{ subtitle }}</p>
                <slot />
            </div>
            <div class="form-foot">模拟交易环境·数据仅供学习演示</div>
        </section>
    </div>
</template>

<style scoped>
.auth {
    display: flex;
    height: 100%;
    background: var(--bg-body);
}

.brand-side {
    position: relative;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    flex: 0 0 46%;
    padding: 34px 44px;
    overflow: hidden;
    background: linear-gradient(150deg, #b62b30 0%, #d13a3f 42%, #e5484d 100%);
    color: #fff;
}

.brand-top {
    display: flex;
    align-items: center;
    gap: 11px;
    z-index: 1;
}

.logo {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 44px;
    height: 44px;
    border-radius: 11px;
    background: rgba(255, 255, 255, 0.96);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
}

.logo img {
    display: block;
    width: 38px;
    height: 38px;
    border-radius: 9px;
}

.brand-text {
    line-height: 1.2;
}

.brand-name {
    font-size: 19px;
    font-weight: 700;
    letter-spacing: 0.3px;
}

.brand-sub {
    font-size: 12px;
    opacity: 0.7;
    letter-spacing: 1px;
}

.brand-main {
    z-index: 1;
    max-width: 440px;
}

.slogan {
    font-size: 32px;
    font-weight: 700;
    line-height: 1.35;
    letter-spacing: 0.5px;
}

.slogan-sub {
    margin-top: 12px;
    font-size: 15px;
    opacity: 0.82;
    line-height: 1.7;
}

.feature-list {
    margin-top: 34px;
    display: flex;
    flex-direction: column;
    gap: 14px;
}

.feature-list li {
    display: flex;
    align-items: flex-start;
    gap: 10px;
    font-size: 15px;
    opacity: 0.9;
    line-height: 1.6;
}

.dot {
    flex: 0 0 auto;
    width: 6px;
    height: 6px;
    margin-top: 8px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.85);
}

.brand-foot {
    z-index: 1;
    font-size: 13px;
    opacity: 0.6;
}

.grid-bg {
    position: absolute;
    inset: 0;
    background-image: linear-gradient(rgba(255, 255, 255, 0.055) 1px, transparent 1px),
        linear-gradient(90deg, rgba(255, 255, 255, 0.055) 1px, transparent 1px);
    background-size: 48px 48px;
    pointer-events: none;
}

.glow {
    position: absolute;
    border-radius: 50%;
    filter: blur(70px);
    pointer-events: none;
}

.glow-1 {
    width: 420px;
    height: 420px;
    right: -140px;
    top: -120px;
    background: rgba(255, 255, 255, 0.16);
}

.glow-2 {
    width: 340px;
    height: 340px;
    right: 40px;
    bottom: -160px;
    background: rgba(0, 0, 0, 0.14);
}

.form-side {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    flex: 1 1 auto;
    padding: 24px;
    gap: 18px;
}

.form-card {
    width: 386px;
    max-width: 100%;
    padding: 34px 34px 28px;
    background: var(--bg-panel);
    border: 1px solid var(--border);
    border-radius: 12px;
    box-shadow: var(--shadow-md);
}

.title {
    font-size: 23px;
    font-weight: 700;
    color: var(--text-primary);
    letter-spacing: 0.3px;
}

.subtitle {
    margin-top: 6px;
    font-size: 14px;
    color: var(--text-secondary);
}

.form-foot {
    font-size: 13px;
    color: var(--text-placeholder);
}
</style>
