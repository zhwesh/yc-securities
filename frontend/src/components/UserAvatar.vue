<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { isAvatarFailed, markAvatarFailed } from '@/utils/avatar'

const props = withDefaults(
    defineProps<{
        src?: string | null
        name?: string | null
        size?: number
        // 加载超时时长，单位毫秒
        timeout?: number
    }>(),
    {
        src: '',
        name: '',
        size: 30,
        timeout: 3000
    }
)

// 加载失败标记
const failed = ref(false)
// 超时定时器句柄
let timer: number | null = null

/**
 * 清除超时定时器
 */
function clearTimer(): void {
    if (timer !== null) {
        window.clearTimeout(timer)
        timer = null
    }
}

// 规范化后的资源地址
const normalizedSrc = computed(() => (props.src ?? '').trim())

// 是否显示图片
const showImg = computed(() => !!normalizedSrc.value && !failed.value)

// 文字头像内容
const initials = computed(() => {
    const n = (props.name ?? '').trim()
    return n ? n.slice(0, 1).toUpperCase() : 'U'
})

// 头像容器样式
const boxStyle = computed(() => ({
    width: `${props.size}px`,
    height: `${props.size}px`,
    fontSize: `${Math.max(10, Math.round(props.size * 0.4))}px`
}))

/**
 * 标记加载失败并记录资源地址
 */
function giveUp(): void {
    clearTimer()
    if (!showImg.value) return
    failed.value = true
    markAvatarFailed(normalizedSrc.value)
}

/**
 * 启动超时计时
 */
function startTimer(): void {
    clearTimer()
    if (!showImg.value) return
    timer = window.setTimeout(giveUp, props.timeout)
}

/**
 * 处理图片加载完成
 */
function onLoad(): void {
    clearTimer()
}

/**
 * 处理图片加载失败
 */
function onError(): void {
    giveUp()
}

watch(
    normalizedSrc,
    (url) => {
        failed.value = !!url && isAvatarFailed(url)
        startTimer()
    },
    { immediate: true }
)

onBeforeUnmount(clearTimer)
</script>

<template>
    <span class="ava" :style="boxStyle" :title="name || undefined">
        <img
            v-if="showImg"
            :src="normalizedSrc"
            alt=""
            referrerpolicy="no-referrer"
            decoding="async"
            @load="onLoad"
            @error="onError"
        />
        <span v-else class="txt">{{ initials }}</span>
    </span>
</template>

<style scoped>
.ava {
    position: relative;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    flex: 0 0 auto;
    border-radius: 50%;
    overflow: hidden;
    background: var(--primary-weak);
    color: var(--primary);
    font-weight: 600;
    line-height: 1;
    user-select: none;
}

.ava img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
}

.txt {
    transform: translateY(0.5px);
}
</style>
