<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import AuthShell from '@/components/AuthShell.vue'
import { userApi } from '@/api/user'
import { useSmsCountdown } from '@/composables/useCountdown'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const { seconds, running, start } = useSmsCountdown(60)

const formRef = ref<FormInstance>()
const loading = ref(false)
const sending = ref(false)

const form = reactive({
    phone: '',
    code: '',
    username: '',
    password: '',
    confirm: '',
    name: '',
    idNumber: ''
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
    username: [
        { required: true, message: '请输入用户名', trigger: 'blur' },
        { min: 3, max: 20, message: '用户名长度3~20个字符', trigger: 'blur' }
    ],
    password: [
        { required: true, message: '请设置登录密码', trigger: 'blur' },
        { min: 6, max: 32, message: '密码长度6~32个字符', trigger: 'blur' }
    ],
    confirm: [
        { required: true, message: '请再次输入密码', trigger: 'blur' },
        {
            validator: (_r, value: string, cb: (e?: Error) => void) => {
                if (value !== form.password) cb(new Error('两次输入的密码不一致'))
                else cb()
            },
            trigger: 'blur'
        }
    ],
    name: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
    idNumber: [
        { required: true, message: '请输入身份证号', trigger: 'blur' },
        { pattern: /^\d{17}[\dXx]$/, message: '身份证号应为18位', trigger: 'blur' }
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
        await auth.register({
            phone: form.phone.trim(),
            code: form.code.trim(),
            username: form.username.trim(),
            password: form.password,
            name: form.name.trim(),
            idNumber: form.idNumber.trim().toUpperCase()
        })
        ElMessage.success('注册成功，已自动登录')
        await router.replace('/')
    } catch {
        /* 拦截器已提示 */
    } finally {
        loading.value = false
    }
}
</script>

<template>
    <AuthShell title="注册" subtitle="创建账号后即可开通资金账户并开始模拟交易">
        <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-position="top"
            class="reg-form"
            @submit.prevent="submit"
        >
            <el-form-item label="手机号" prop="phone">
                <el-input v-model="form.phone" placeholder="请输入手机号" maxlength="11">
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

            <div class="dev-hint">验证码5分钟内有效，请勿转发给他人。</div>

            <div class="grid-2">
                <el-form-item label="用户名" prop="username">
                    <el-input v-model="form.username" placeholder="登录用户名" maxlength="20" />
                </el-form-item>
                <el-form-item label="姓名" prop="name">
                    <el-input v-model="form.name" placeholder="真实姓名" maxlength="20" />
                </el-form-item>
            </div>

            <el-form-item label="身份证号" prop="idNumber">
                <el-input v-model="form.idNumber" placeholder="18位身份证号" maxlength="18" />
            </el-form-item>

            <div class="grid-2">
                <el-form-item label="登录密码" prop="password">
                    <el-input
                        v-model="form.password"
                        type="password"
                        show-password
                        placeholder="6~32位"
                        maxlength="32"
                    />
                </el-form-item>
                <el-form-item label="确认密码" prop="confirm">
                    <el-input
                        v-model="form.confirm"
                        type="password"
                        show-password
                        placeholder="再次输入"
                        maxlength="32"
                        @keyup.enter="submit"
                    />
                </el-form-item>
            </div>

            <el-button type="primary" class="submit" :loading="loading" @click="submit">注册并登录</el-button>
        </el-form>

        <div class="links">
            <span class="tip">已有账号？</span>
            <router-link to="/login" class="link">返回登录</router-link>
        </div>
    </AuthShell>
</template>

<style scoped>
.reg-form {
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

.dev-hint {
    margin: -8px 0 14px;
    font-size: 13px;
    color: var(--text-placeholder);
}

.grid-2 {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 0 12px;
}

.submit {
    width: 100%;
    margin-top: 10px;
    letter-spacing: 1px;
}

.links {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    margin-top: 16px;
    font-size: 14px;
}

.tip {
    color: var(--text-secondary);
}

.link {
    color: var(--primary);
}
</style>
