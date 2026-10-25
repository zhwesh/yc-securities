<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import AuthShell from '@/components/AuthShell.vue'
import { userApi } from '@/api/user'
import { useSmsCountdown } from '@/composables/useCountdown'

const router = useRouter()
const { seconds, running, start } = useSmsCountdown(60)

const formRef = ref<FormInstance>()
const loading = ref(false)
const sending = ref(false)

const form = reactive({
    phone: '',
    code: '',
    newPassword: '',
    confirm: ''
})

const rules: FormRules = {
    phone: [
        { required: true, message: '请输入手机号', trigger: 'blur' },
        { pattern: /^1\d{10}$/, message: '手机号格式不正确', trigger: 'blur' }
    ],
    code: [
        { required: true, message: '请输入验证码', trigger: 'blur' },
        { pattern: /^\d{6}$/, message: '验证码为6位数字', trigger: 'blur' }
    ],
    newPassword: [
        { required: true, message: '请输入新密码', trigger: 'blur' },
        { min: 6, max: 32, message: '密码长度6~32个字符', trigger: 'blur' }
    ],
    confirm: [
        { required: true, message: '请再次输入新密码', trigger: 'blur' },
        {
            validator: (_r, value: string, cb: (e?: Error) => void) => {
                if (value !== form.newPassword) cb(new Error('两次输入的密码不一致'))
                else cb()
            },
            trigger: 'blur'
        }
    ]
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
        /* 拦截器已提示 */
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
        await userApi.resetPassword(form.phone.trim(), form.code.trim(), form.newPassword)
        ElMessage.success('密码已重置，请重新登录')
        await router.replace({ name: 'login' })
    } catch {
        /* 拦截器已提示 */
    } finally {
        loading.value = false
    }
}
</script>

<template>
    <AuthShell title="找回密码" subtitle="通过手机验证码重置登录密码">
        <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-position="top"
            class="reset-form"
            @submit.prevent="submit"
        >
            <el-form-item label="手机号" prop="phone">
                <el-input v-model="form.phone" placeholder="请输入注册手机号" maxlength="11">
                    <template #prefix><el-icon><Iphone /></el-icon></template>
                </el-input>
            </el-form-item>

            <el-form-item label="验证码" prop="code">
                <div class="code-row">
                    <el-input v-model="form.code" placeholder="6位验证码" maxlength="6" />
                    <el-button class="code-btn" :disabled="running" :loading="sending" @click="sendCode">
                        {{ running ? `${seconds}s` : '获取验证码' }}
                    </el-button>
                </div>
            </el-form-item>

            <el-form-item label="新密码" prop="newPassword">
                <el-input
                    v-model="form.newPassword"
                    type="password"
                    show-password
                    placeholder="6~32位新密码"
                    maxlength="32"
                />
            </el-form-item>

            <el-form-item label="确认新密码" prop="confirm">
                <el-input
                    v-model="form.confirm"
                    type="password"
                    show-password
                    placeholder="再次输入新密码"
                    maxlength="32"
                    @keyup.enter="submit"
                />
            </el-form-item>

            <el-button type="primary" class="submit" :loading="loading" @click="submit">
                重置密码
            </el-button>
        </el-form>

        <div class="links">
            <router-link to="/login" class="link">返回登录</router-link>
        </div>
    </AuthShell>
</template>

<style scoped>
.reset-form {
    margin-top: 20px;
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

.submit {
    width: 100%;
    margin-top: 6px;
    letter-spacing: 1px;
}

.links {
    display: flex;
    justify-content: center;
    margin-top: 16px;
    font-size: 14px;
}

.link {
    color: var(--primary);
}
</style>
