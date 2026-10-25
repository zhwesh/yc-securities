<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type UploadRequestOptions } from 'element-plus'
import { userApi } from '@/api/user'
import UserAvatar from '@/components/UserAvatar.vue'
import { clearAvatarFailed } from '@/utils/avatar'
import { useAccountStore } from '@/stores/account'
import { useAuthStore } from '@/stores/auth'
import { useMarketStore } from '@/stores/market'
import { useSmsCountdown } from '@/composables/useCountdown'
import { fmtDate, maskPhone } from '@/utils/format'

const auth = useAuthStore()
const account = useAccountStore()
const market = useMarketStore()
const router = useRouter()

const {
    seconds: smsSeconds,
    running: smsRunning,
    start: startSms
} = useSmsCountdown(60)

const user = computed(() => auth.user)

/* ------------------------------ 头像 ------------------------------ */

const uploading = ref(false)

async function customUpload(options: UploadRequestOptions): Promise<void> {
    const file = options.file as File
    if (file.size > 2 * 1024 * 1024) {
        ElMessage.warning('头像不能超过2MB')
        options.onError(new Error('too large') as never)
        return
    }
    if (!/^image\/(png|jpe?g|webp|gif)$/.test(file.type)) {
        ElMessage.warning('请选择 png / jpg / webp / gif 图片')
        options.onError(new Error('bad type') as never)
        return
    }
    uploading.value = true
    try {
        const url = await userApi.uploadAvatar(file)
        await userApi.update({ image: url })
        await auth.fetchProfile()
        // 新头像 URL 变了，清掉旧的失败记录，让它重新尝试加载
        clearAvatarFailed()
        ElMessage.success('头像已更新')
        options.onSuccess?.({ url })
    } catch {
        options.onError?.(new Error('upload failed') as never)
    } finally {
        uploading.value = false
    }
}

function beforeAvatarUpload(file: File): boolean {
    if (file.size > 2 * 1024 * 1024) {
        ElMessage.warning('头像不能超过2MB')
        return false
    }
    return true
}

/* ------------------------------ 用户名 ------------------------------ */

const usernameForm = reactive({ username: '' })
const usernameLoading = ref(false)

async function saveUsername(): Promise<void> {
    const v = usernameForm.username.trim()
    if (!v) {
        ElMessage.warning('请输入用户名')
        return
    }
    if (v === user.value?.username) {
        ElMessage.info('用户名未修改')
        return
    }
    usernameLoading.value = true
    try {
        await userApi.update({ username: v })
        await auth.fetchProfile()
        ElMessage.success('用户名已更新')
    } catch {
        /* 拦截器已提示 */
    } finally {
        usernameLoading.value = false
    }
}

/* ------------------------------ 换绑手机号 ------------------------------ */

const phoneForm = reactive({ phone: '', code: '' })
const phoneLoading = ref(false)
const sendingCode = ref(false)

async function sendPhoneCode(): Promise<void> {
    if (smsRunning.value || sendingCode.value) return
    if (!/^1\d{10}$/.test(phoneForm.phone)) {
        ElMessage.warning('请输入正确的手机号')
        return
    }
    sendingCode.value = true
    try {
        await userApi.sendSmsCode(phoneForm.phone)
        startSms()
        ElMessage.success('验证码已发送，请注意查收')
    } catch {
        /* 拦截器已提示 */
    } finally {
        sendingCode.value = false
    }
}

async function savePhone(): Promise<void> {
    if (!/^1\d{10}$/.test(phoneForm.phone)) {
        ElMessage.warning('请输入正确的手机号')
        return
    }
    if (!phoneForm.code) {
        ElMessage.warning('请输入验证码')
        return
    }
    phoneLoading.value = true
    try {
        await userApi.update({ phone: phoneForm.phone, code: phoneForm.code })
        await auth.fetchProfile()
        phoneForm.phone = ''
        phoneForm.code = ''
        ElMessage.success('手机号已更新')
    } catch {
        /* 拦截器已提示 */
    } finally {
        phoneLoading.value = false
    }
}

/* ------------------------------ 退出 ------------------------------ */

async function logout(): Promise<void> {
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

// 用户信息就绪（或更新）后同步用户名输入框
watch(
    () => auth.user?.username,
    (v) => {
        usernameForm.username = v ?? ''
    },
    { immediate: true }
)
</script>

<template>
    <div class="profile">
        <!-- 左侧资料卡 -->
        <section class="panel id-card">
            <div class="id-top">
                <el-upload
                    class="avatar-upload"
                    :show-file-list="false"
                    :http-request="customUpload"
                    :before-upload="beforeAvatarUpload"
                    accept="image/*"
                >
                    <div v-loading="uploading" class="avatar-wrap">
                        <UserAvatar :src="auth.avatar" :name="user?.name || user?.username" :size="76" />
                        <div class="avatar-mask">更换头像</div>
                    </div>
                </el-upload>

                <div class="id-info">
                    <h2 class="name">{{ user?.name || '--' }}</h2>
                    <div class="uname">@{{ user?.username }}</div>
                    <div class="id-tags">
                        <el-tag size="small" effect="plain">模拟账户</el-tag>
                        <el-tag v-if="account.accounts.length" size="small" type="success" effect="plain">
                            已开通资金账户
                        </el-tag>
                    </div>
                </div>
            </div>

            <ul class="id-meta">
                <li>
                    <span class="k">用户 ID</span>
                    <span class="v num">{{ user?.id ?? '--' }}</span>
                </li>
                <li>
                    <span class="k">手机号</span>
                    <span class="v num">{{ maskPhone(user?.phone) }}</span>
                </li>
                <li>
                    <span class="k">注册时间</span>
                    <span class="v num">{{ fmtDate(user?.createTime) }}</span>
                </li>
                <li>
                    <span class="k">资金账户</span>
                    <span class="v num">{{ account.accounts.length }}个</span>
                </li>
            </ul>
        </section>

        <!-- 右侧设置 -->
        <div class="settings">
            <section class="panel">
                <div class="panel-title">修改用户名</div>
                <div class="form-body">
                    <el-input
                        v-model="usernameForm.username"
                        placeholder="3~20个字符，需唯一"
                        maxlength="20"
                        class="w-320"
                    />
                    <el-button type="primary" :loading="usernameLoading" @click="saveUsername">
                        保存
                    </el-button>
                </div>
            </section>

            <section class="panel">
                <div class="panel-title">换绑手机号</div>
                <div class="form-body">
                    <div class="line">
                        <el-input v-model="phoneForm.phone" placeholder="新手机号" maxlength="11" class="w-200">
                            <template #prefix><el-icon><Iphone /></el-icon></template>
                        </el-input>
                        <el-button :disabled="smsRunning" :loading="sendingCode" @click="sendPhoneCode">
                            {{ smsRunning ? `${smsSeconds}s` : '获取验证码' }}
                        </el-button>
                    </div>
                    <div class="line">
                        <el-input v-model="phoneForm.code" placeholder="6位验证码" maxlength="6" class="w-200" />
                        <el-button type="primary" :loading="phoneLoading" @click="savePhone">换绑</el-button>
                    </div>
                    <div class="hint">当前手机号{{ maskPhone(user?.phone) }}，换绑后请用新手机号登录。</div>
                </div>
            </section>

            <section class="panel">
                <div class="panel-title">账户安全</div>
                <div class="security">
                    <div class="sec-item">
                        <div class="sec-text">
                            <div class="sec-title">登录密码</div>
                            <div class="sec-desc">通过短信验证码重置登录密码</div>
                        </div>
                        <el-button size="small" @click="router.push('/reset-password')">去重置</el-button>
                    </div>

                    <div class="sec-item">
                        <div class="sec-text">
                            <div class="sec-title">交易密码</div>
                            <div class="sec-desc">在资金页修改，用于下单与出金</div>
                        </div>
                        <el-button size="small" @click="router.push('/assets')">去管理</el-button>
                    </div>

                    <div class="sec-item">
                        <div class="sec-text">
                            <div class="sec-title">资金账户</div>
                            <div class="sec-desc">
                                {{ account.accounts.length ? `共 ${account.accounts.length} 个账户` : '尚未开通' }}
                            </div>
                        </div>
                        <el-button size="small" @click="router.push('/assets')">去查看</el-button>
                    </div>

                    <div class="sec-item">
                        <div class="sec-text">
                            <div class="sec-title">退出登录</div>
                            <div class="sec-desc">退出后将断开实时行情</div>
                        </div>
                        <el-button size="small" type="danger" plain @click="logout">退出登录</el-button>
                    </div>
                </div>
            </section>
        </div>
    </div>
</template>

<style scoped>
.profile {
    display: grid;
    grid-template-columns: 320px minmax(0, 1fr);
    gap: 10px;
    align-items: start;
}

/* ------------------------------ 资料卡 ------------------------------ */

.id-card {
    padding: 18px;
}

.id-top {
    display: flex;
    align-items: center;
    gap: 16px;
}

.avatar-upload :deep(.el-upload) {
    border-radius: 50%;
}

.avatar-wrap {
    position: relative;
    width: 76px;
    height: 76px;
    border-radius: 50%;
    overflow: hidden;
    cursor: pointer;
}

.avatar-mask {
    position: absolute;
    left: 0;
    right: 0;
    bottom: -22px;
    height: 22px;
    background: rgba(0, 0, 0, 0.55);
    color: #fff;
    font-size: 12px;
    line-height: 22px;
    text-align: center;
    transition: bottom 0.18s;
}

.avatar-wrap:hover .avatar-mask {
    bottom: 0;
}

.id-info {
    min-width: 0;
}

.name {
    font-size: 20px;
    font-weight: 700;
    color: var(--text-primary);
}

.uname {
    margin-top: 2px;
    font-size: 14px;
    color: var(--text-secondary);
}

.id-tags {
    display: flex;
    gap: 6px;
    margin-top: 8px;
    flex-wrap: wrap;
}

.id-meta {
    margin-top: 18px;
    display: flex;
    flex-direction: column;
    gap: 2px;
}

.id-meta li {
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 30px;
    border-bottom: 1px dashed var(--divider);
    font-size: 14px;
}

.id-meta .k {
    color: var(--text-secondary);
}

.id-meta .v {
    color: var(--text-primary);
}

/* ------------------------------ 设置 ------------------------------ */

.settings {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.form-body {
    display: flex;
    flex-direction: column;
    gap: 10px;
    padding: 14px;
}

.line {
    display: flex;
    gap: 8px;
    align-items: center;
}

.w-320 {
    width: 320px;
}

.w-200 {
    width: 200px;
}

.hint {
    font-size: 13px;
    color: var(--text-placeholder);
}

.security {
    display: flex;
    flex-direction: column;
}

.sec-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 12px 14px;
    border-bottom: 1px solid var(--divider);
}

.sec-item:last-child {
    border-bottom: none;
}

.sec-title {
    font-size: 15px;
    color: var(--text-primary);
}

.sec-desc {
    margin-top: 2px;
    font-size: 13px;
    color: var(--text-placeholder);
}
</style>
