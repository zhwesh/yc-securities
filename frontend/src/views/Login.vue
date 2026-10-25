<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import AuthShell from '@/components/AuthShell.vue'
import { userApi } from '@/api/user'
import { useSmsCountdown } from '@/composables/useCountdown'
import { useAuthStore } from '@/stores/auth'

type Mode = 'password' | 'phone'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const { seconds, running, start } = useSmsCountdown(60)

const mode = ref<Mode>('password')
const loading = ref(false)
const sending = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
    username: '',
    password: '',
    phone: '',
    code: ''
})

const rules: FormRules = {
    username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
    password: [{ required: true, message: '请输入登录密码', trigger: 'blur' }],
    phone: [
        { required: true, message: '请输入手机号', trigger: 'blur' },
        { pattern: /^1\d{10}$/, message: '手机号格式不正确', trigger: 'blur' }
    ],
    code: [
        { required: true, message: '请输入验证码', trigger: 'blur' },
        { pattern: /^\d{6}$/, message: '验证码为6位数字', trigger: 'blur' }
    ]
}

/** 校验站内跳转路径 */
function redirectTarget(): string {
    const raw = route.query.redirect
    const path = typeof raw === 'string' ? raw : '/'
    return path.startsWith('/') && !path.startsWith('//') ? path : '/'
}

async function sendCode(): Promise<void> {
    if (running.value || sending.value) return
    if (!/^1\d{10}$/.test(form.phone)) {
        ElMessage.warning('请先输入正确的手机号')
        return
    }
    sending.value = true
    try {
        await userApi.sendSmsCode(form.phone)
        start()
        ElMessage.success('验证码已发送，请注意查收')
    } catch {
        /* 错误提示由拦截器统一处理 */
    } finally {
        sending.value = false
    }
}

async function submit(): Promise<void> {
    if (loading.value) return
    try {
        await formRef.value?.validate()
    } catch {
        return
    }

    loading.value = true
    try {
        if (mode.value === 'password') {
            await auth.loginByUsername(form.username.trim(), form.password)
        } else {
            await auth.loginByPhone(form.phone.trim(), form.code.trim())
        }
        ElMessage.success(`欢迎回来，${auth.displayName}`)
        await router.replace(redirectTarget())
    } catch {
        /* 错误提示由拦截器统一处理 */
    } finally {
        loading.value = false
    }
}
</script>

<template>
    <AuthShell
        title="登录"
        subtitle="使用用户名密码或手机验证码登录交易终端"
    >
        <el-tabs v-model="mode" class="login-tabs">
            <el-tab-pane label="用户名登录" name="password" />
            <el-tab-pane label="手机验证码" name="phone" />
        </el-tabs>

        <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-position="top"
            size="large"
            class="login-form"
            @submit.prevent="submit"
        >
            <template v-if="mode === 'password'">
                <el-form-item label="用户名" prop="username">
                    <el-input v-model="form.username" placeholder="请输入用户名" autocomplete="username">
                        <template #prefix><el-icon><User /></el-icon></template>
                    </el-input>
                </el-form-item>
                <el-form-item label="登录密码" prop="password">
                    <el-input
                        v-model="form.password"
                        type="password"
                        show-password
                        placeholder="请输入登录密码"
                        autocomplete="current-password"
                        @keyup.enter="submit"
                    >
                        <template #prefix><el-icon><Lock /></el-icon></template>
                    </el-input>
                </el-form-item>
            </template>

            <template v-else>
                <el-form-item label="手机号" prop="phone">
                    <el-input v-model="form.phone" placeholder="请输入手机号" maxlength="11">
                        <template #prefix><el-icon><Iphone /></el-icon></template>
                    </el-input>
                </el-form-item>
                <el-form-item label="验证码" prop="code">
                    <div class="code-row">
                        <el-input v-model="form.code" placeholder="6位验证码" maxlength="6" />
                        <el-button
                            class="code-btn"
                            :disabled="running"
                            :loading="sending"
                            @click="sendCode"
                        >
                            {{ running ? `${seconds}s` : '获取验证码' }}
                        </el-button>
                    </div>
                </el-form-item>
                <div class="dev-hint">验证码5分钟内有效，请勿转发给他人。</div>
            </template>

            <el-button type="primary" class="submit" :loading="loading" @click="submit">
                登录
            </el-button>
        </el-form>

        <div class="links">
            <router-link to="/reset-password" class="link">忘记密码？</router-link>
            <span class="split" />
            <router-link to="/register" class="link">注册新账号</router-link>
        </div>
    </AuthShell>
</template>

<style scoped>
.login-tabs {
    margin-top: 18px;
}

.login-form {
    margin-top: 14px;
}

.code-row {
    display: flex;
    gap: 8px;
    width: 100%;
}

.code-btn {
    flex: 0 0 108px;
    font-variant-numeric: tabular-nums;
}

.dev-hint {
    margin: -8px 0 12px;
    font-size: 13px;
    line-height: 1.6;
    color: var(--text-placeholder);
}

.submit {
    width: 100%;
    margin-top: 4px;
    font-size: 16px;
    letter-spacing: 2px;
}

.links {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 12px;
    margin-top: 18px;
    font-size: 14px;
}

.link {
    color: var(--text-secondary);
    transition: color 0.15s;
}

.link:hover {
    color: var(--primary);
}

.split {
    width: 1px;
    height: 11px;
    background: var(--border-strong);
}
</style>
