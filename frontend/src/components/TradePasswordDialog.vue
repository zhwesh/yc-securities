<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useTradeAuthStore, type OnlineHours } from '@/stores/tradeAuth'

// 交易密码在线状态仓库
const tradeAuth = useTradeAuthStore()
// 输入的交易密码
const password = ref('')
// 密码输入框引用
const inputRef = ref()

// 弹框标题
const title = computed(() =>
    tradeAuth.accountId ? `验证交易密码（资金账号${tradeAuth.accountId}）` : '验证交易密码'
)

watch(
    () => tradeAuth.visible,
    (v) => {
        if (v) {
            password.value = ''
            tradeAuth.error = ''
            window.setTimeout(() => inputRef.value?.focus?.(), 60)
        }
    }
)

/**
 * 提交交易密码
 */
async function submit(): Promise<void> {
    const ok = await tradeAuth.submit(password.value)
    if (ok) tradeAuth.confirmSuccess()
}

/**
 * 处理弹框关闭完成
 */
function onClosed(): void {
    password.value = ''
}

/**
 * 设置保持在线时长
 * @param v 时长，单位小时
 */
function setHours(v: OnlineHours): void {
    tradeAuth.onlineHours = v
}
</script>

<template>
    <el-dialog
        :model-value="tradeAuth.visible"
        :title="title"
        width="400px"
        :close-on-click-modal="false"
        :close-on-press-escape="!tradeAuth.busy"
        append-to-body
        @update:model-value="(v: boolean) => !v && tradeAuth.cancel()"
        @closed="onClosed"
    >
        <div class="tip">
            该操作需要验证交易密码。验证后在所选时长内，查询持仓与出金等操作无需重复输入。
        </div>

        <el-form label-width="76px" @submit.prevent>
            <el-form-item label="交易密码">
                <el-input
                    ref="inputRef"
                    v-model="password"
                    type="password"
                    show-password
                    placeholder="请输入6位交易密码"
                    maxlength="20"
                    :disabled="tradeAuth.busy"
                    @keyup.enter="submit"
                />
            </el-form-item>
            <el-form-item label="保持在线">
                <el-radio-group :model-value="tradeAuth.onlineHours" :disabled="tradeAuth.busy">
                    <el-radio-button
                        v-for="h in ([6, 12, 24] as OnlineHours[])"
                        :key="h"
                        :value="h"
                        @click="setHours(h)"
                    >
                        {{ h }}小时
                    </el-radio-button>
                </el-radio-group>
            </el-form-item>
        </el-form>

        <div v-if="tradeAuth.error" class="err">{{ tradeAuth.error }}</div>

        <template #footer>
            <el-button :disabled="tradeAuth.busy" @click="tradeAuth.cancel()">取消</el-button>
            <el-button type="primary" :loading="tradeAuth.busy" @click="submit">验证</el-button>
        </template>
    </el-dialog>
</template>

<style scoped>
.tip {
    margin-bottom: 14px;
    padding: 8px 10px;
    border-radius: 4px;
    background: var(--bg-subtle);
    color: var(--text-secondary);
    font-size: 14px;
    line-height: 1.6;
}

.err {
    margin-top: 4px;
    color: var(--up);
    font-size: 14px;
}
</style>
