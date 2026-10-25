<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { accountApi, bankCardApi } from '@/api/account'
import { ApiError } from '@/api/http'
import { useAccountStore } from '@/stores/account'
import { useTradeAuthStore } from '@/stores/tradeAuth'
import type { BankCardVO } from '@/types'
import { ACCOUNT_STATUS_TEXT } from '@/types'
import { useSmsCountdown } from '@/composables/useCountdown'
import { fmtDateTime, fmtMoney } from '@/utils/format'

const account = useAccountStore()
const tradeAuth = useTradeAuthStore()
const {
    seconds: smsSeconds,
    running: smsRunning,
    start: startSms
} = useSmsCountdown(60)

const BANKS = [
    '工商银行',
    '建设银行',
    '农业银行',
    '中国银行',
    '交通银行',
    '招商银行',
    '浦发银行',
    '中信银行',
    '民生银行',
    '兴业银行',
    '平安银行',
    '光大银行'
]

const BOARD_OPTIONS = [
    { name: '创业板', threshold: '10万元' },
    { name: '科创板', threshold: '50万元' },
    { name: '北交所', threshold: '50万元' }
]

const current = computed(() => account.current)
const currentId = computed(() => account.currentId)

/* ------------------------------ 开户 ------------------------------ */

const openVisible = ref(false)
const openForm = reactive({ password: '', confirm: '' })
const opening = ref(false)

function openAccountDialog(): void {
    openForm.password = ''
    openForm.confirm = ''
    openVisible.value = true
}

async function submitOpenAccount(): Promise<void> {
    if (openForm.password.length < 6) {
        ElMessage.warning('交易密码至少6位')
        return
    }
    if (openForm.password !== openForm.confirm) {
        ElMessage.warning('两次输入的交易密码不一致')
        return
    }
    opening.value = true
    try {
        await accountApi.open(openForm.password)
        ElMessage.success('资金账户开通成功，默认开通主板交易权限')
        openVisible.value = false
        await account.loadAccounts()
        await account.loadPositions()
    } catch {
        /* 拦截器已提示 */
    } finally {
        opening.value = false
    }
}

/* ------------------------------ 板块 ------------------------------ */

const boardVisible = ref(false)
const boardPicked = ref<string[]>([])
const openingBoard = ref(false)

const availableBoards = computed(() =>
    BOARD_OPTIONS.filter((b) => !(current.value?.boards ?? []).includes(b.name))
)

function openBoardDialog(): void {
    boardPicked.value = []
    boardVisible.value = true
}

async function submitBoard(): Promise<void> {
    const id = currentId.value
    if (!id || !boardPicked.value.length) {
        ElMessage.warning('请选择要开通的板块')
        return
    }
    openingBoard.value = true
    try {
        await accountApi.openBoard(id, boardPicked.value)
        ElMessage.success('板块交易权限开通成功')
        boardVisible.value = false
        await account.loadAccounts()
    } catch {
        /* 拦截器已提示 */
    } finally {
        openingBoard.value = false
    }
}

/* ------------------------------ 出入金 ------------------------------ */

const moneyTab = ref<'deposit' | 'withdraw'>('deposit')
const amount = ref<number>(10000)
const moneyLoading = ref(false)

async function submitMoney(): Promise<void> {
    const id = currentId.value
    if (!id) return
    if (!amount.value || amount.value <= 0) {
        ElMessage.warning('请输入正确的金额')
        return
    }

    if (moneyTab.value === 'withdraw') {
        // 出金必须交易密码在线态
        const ok = await tradeAuth.ensure(id)
        if (!ok) return
    }

    moneyLoading.value = true
    try {
        if (moneyTab.value === 'deposit') {
            await accountApi.deposit(id, amount.value)
            ElMessage.success(`入金 ${fmtMoney(amount.value)} 元成功`)
        } else {
            await accountApi.withdraw(id, amount.value)
            ElMessage.success(`出金 ${fmtMoney(amount.value)} 元成功`)
        }
        await account.loadAccounts()
        await account.loadPositions()
    } catch (e) {
        const msg = e instanceof ApiError ? e.message : '操作失败'
        if (msg.includes('交易密码')) {
            // 本地在线态与后端不一致，清掉提示重新验证
            tradeAuth.invalidate(id)
            ElMessage.warning('交易密码验证已失效，请重新验证')
        }
    } finally {
        moneyLoading.value = false
    }
}

function setQuick(v: number): void {
    amount.value = v
}

/* ------------------------------ 交易密码 ------------------------------ */

const pwdVisible = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const pwdLoading = ref(false)

function openPwdDialog(): void {
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirm = ''
    pwdVisible.value = true
}

async function submitPassword(): Promise<void> {
    const id = currentId.value
    if (!id) return
    if (pwdForm.newPassword.length < 6) {
        ElMessage.warning('新交易密码至少6位')
        return
    }
    if (pwdForm.newPassword !== pwdForm.confirm) {
        ElMessage.warning('两次输入的新密码不一致')
        return
    }
    pwdLoading.value = true
    try {
        await accountApi.updateTradePassword(id, pwdForm.oldPassword, pwdForm.newPassword)
        // 后端会清除在线态
        tradeAuth.invalidate(id)
        ElMessage.success('交易密码已修改，请重新验证后使用')
        pwdVisible.value = false
    } catch {
        /* 拦截器已提示 */
    } finally {
        pwdLoading.value = false
    }
}

const onlineMinutes = computed(() =>
    currentId.value ? tradeAuth.remainingMinutes(currentId.value) : 0
)

async function verifyNow(): Promise<void> {
    const id = currentId.value
    if (!id) return
    const ok = await tradeAuth.prompt(id)
    if (ok) {
        await account.loadPositions()
        ElMessage.success('交易密码验证成功')
    }
}

/* ------------------------------ 银行卡 ------------------------------ */

const cards = ref<BankCardVO[]>([])
const cardsLoading = ref(false)

async function loadCards(): Promise<void> {
    const id = currentId.value
    if (!id) {
        cards.value = []
        return
    }
    cardsLoading.value = true
    try {
        cards.value = await bankCardApi.list(id)
    } catch {
        cards.value = []
    } finally {
        cardsLoading.value = false
    }
}

const bindVisible = ref(false)
const bindForm = reactive({ cardNumber: '', bankName: '工商银行', phone: '', code: '' })
const bindLoading = ref(false)
const verifying = ref(false)

function openBindDialog(): void {
    bindForm.cardNumber = ''
    bindForm.bankName = '工商银行'
    bindForm.phone = ''
    bindForm.code = ''
    bindVisible.value = true
}

/** 后端要求：先校验卡号与预留手机号匹配，再发验证码，最后提交绑定 */
async function verifyAndSendCode(): Promise<void> {
    if (!/^\d{12,19}$/.test(bindForm.cardNumber)) {
        ElMessage.warning('请输入正确的银行卡号')
        return
    }
    if (!/^1\d{10}$/.test(bindForm.phone)) {
        ElMessage.warning('请输入正确的银行预留手机号')
        return
    }
    verifying.value = true
    try {
        await bankCardApi.verify(bindForm.cardNumber, bindForm.phone)
        await bankCardApi.sendCode(bindForm.phone)
        startSms()
        ElMessage.success('验证码已发送，请注意查收')
    } catch {
        /* 拦截器已提示 */
    } finally {
        verifying.value = false
    }
}

async function submitBind(): Promise<void> {
    const id = currentId.value
    if (!id) return
    if (!bindForm.code) {
        ElMessage.warning('请输入短信验证码')
        return
    }
    bindLoading.value = true
    try {
        await bankCardApi.bind({
            accountId: id,
            cardNumber: bindForm.cardNumber,
            bankName: bindForm.bankName,
            phone: bindForm.phone,
            code: bindForm.code
        })
        ElMessage.success('银行卡绑定成功')
        bindVisible.value = false
        await loadCards()
    } catch {
        /* 拦截器已提示 */
    } finally {
        bindLoading.value = false
    }
}

const unbindVisible = ref(false)
const unbindForm = reactive({ cardNumber: '', phone: '', code: '' })
const unbindLoading = ref(false)
const unbindSending = ref(false)

function openUnbindDialog(card: BankCardVO): void {
    unbindForm.cardNumber = card.cardNumber
    unbindForm.phone = ''
    unbindForm.code = ''
    unbindVisible.value = true
}

async function sendUnbindCode(): Promise<void> {
    if (!/^1\d{10}$/.test(unbindForm.phone)) {
        ElMessage.warning('请输入该卡绑定时使用的预留手机号')
        return
    }
    unbindSending.value = true
    try {
        await bankCardApi.verify(unbindForm.cardNumber, unbindForm.phone)
        await bankCardApi.sendCode(unbindForm.phone)
        ElMessage.success('验证码已发送，请注意查收')
    } catch {
        /* 拦截器已提示 */
    } finally {
        unbindSending.value = false
    }
}

async function submitUnbind(): Promise<void> {
    const id = currentId.value
    if (!id) return
    try {
        await ElMessageBox.confirm('解绑后该卡不能再用于出入金，确定继续？', '解绑银行卡', {
            confirmButtonText: '解绑',
            cancelButtonText: '取消',
            type: 'warning'
        })
    } catch {
        return
    }
    unbindLoading.value = true
    try {
        await bankCardApi.unbind({
            accountId: id,
            cardNumber: unbindForm.cardNumber,
            phone: unbindForm.phone,
            code: unbindForm.code
        })
        ElMessage.success('银行卡已解绑')
        unbindVisible.value = false
        await loadCards()
    } catch {
        /* 拦截器已提示 */
    } finally {
        unbindLoading.value = false
    }
}

function maskCard(cardNumber: string): string {
    if (!cardNumber || cardNumber.length < 8) return cardNumber
    return `${cardNumber.slice(0, 4)} **** **** ${cardNumber.slice(-4)}`
}

onMounted(async () => {
    if (!account.loaded) {
        try {
            await account.loadAccounts()
        } catch {
            /* ignore */
        }
    }
    await loadCards()
})
</script>

<template>
    <div class="assets">
        <!-- 账户概览 -->
        <section class="panel overview">
            <div class="ov-head">
                <div class="ov-title">
                    资金账户
                    <span v-if="account.accounts.length" class="count">
                        共{{ account.accounts.length }}个
                    </span>
                </div>
                <div class="ov-actions">
                    <el-button size="small" @click="openAccountDialog">开通新账户</el-button>
                    <el-button
                        size="small"
                        type="primary"
                        :disabled="!current"
                        @click="openBoardDialog"
                    >
                        开通板块
                    </el-button>
                </div>
            </div>

            <div v-if="!account.accounts.length" class="empty-tip">
                还没有资金账户。开通后即可绑定银行卡、入金并开始交易。
            </div>

            <div v-else class="acc-list">
                <div
                    v-for="a in account.accounts"
                    :key="a.id"
                    class="acc-card"
                    :class="{ active: account.currentId === a.id }"
                    @click="account.selectAccount(a.id); loadCards()"
                >
                    <div class="acc-top">
                        <span class="acc-no num">资金账号 #{{ a.id }}</span>
                        <el-tag
                            size="small"
                            :type="a.status === 1 ? 'success' : 'danger'"
                            effect="plain"
                        >
                            {{ ACCOUNT_STATUS_TEXT[a.status] ?? a.status }}
                        </el-tag>
                    </div>
                    <div class="acc-money num">{{ fmtMoney(a.availableAssets) }}</div>
                    <div class="acc-label">可用资产（元）</div>
                    <div class="acc-boards">
                        <el-tag v-for="b in a.boards" :key="b" size="small" effect="plain">{{ b }}</el-tag>
                    </div>
                    <div class="acc-time">开户时间{{ fmtDateTime(a.createTime) }}</div>
                </div>
            </div>
        </section>

        <div class="grid">
            <!-- 出入金 -->
            <section class="panel">
                <div class="panel-title">
                    出入金
                    <span class="extra">
                        验证状态
                        <template v-if="onlineMinutes > 0">剩余{{ Math.floor(onlineMinutes / 60) }}小时</template>
                        <template v-else>未验证</template>
                    </span>
                </div>

                <div class="money-body">
                    <div class="money-tabs">
                        <button
                            class="money-tab"
                            :class="{ active: moneyTab === 'deposit' }"
                            @click="moneyTab = 'deposit'"
                        >
                            入金
                        </button>
                        <button
                            class="money-tab"
                            :class="{ active: moneyTab === 'withdraw' }"
                            @click="moneyTab = 'withdraw'"
                        >
                            出金
                        </button>
                    </div>

                    <div class="amount-row">
                        <span class="lbl">金额</span>
                        <el-input-number
                            v-model="amount"
                            class="num-input"
                            :min="0.01"
                            :step="1000"
                            :precision="2"
                            :controls="false"
                            size="large"
                        />
                        <span class="unit">元</span>
                    </div>

                    <div class="quick-amounts">
                        <button v-for="v in [1000, 5000, 10000, 50000]" :key="v" class="quick" @click="setQuick(v)">
                            {{ v.toLocaleString('en-US') }}
                        </button>
                    </div>

                    <div class="money-tip">
                        <template v-if="moneyTab === 'deposit'">
                            入金前请先绑定银行卡。
                        </template>
                        <template v-else>
                            出金前请先验证交易密码，并确保已绑定银行卡。
                        </template>
                    </div>

                    <div class="money-actions">
                        <el-button
                            type="primary"
                            :loading="moneyLoading"
                            :disabled="!current"
                            @click="submitMoney"
                        >
                            {{ moneyTab === 'deposit' ? '确认入金' : '确认出金' }}
                        </el-button>
                        <el-button v-if="moneyTab === 'withdraw'" :disabled="!current" @click="verifyNow">
                            验证交易密码
                        </el-button>
                    </div>
                </div>
            </section>

            <!-- 交易密码 -->
            <section class="panel">
                <div class="panel-title">
                    交易密码
                    <span class="extra">
                        <el-tag v-if="onlineMinutes > 0" size="small" type="success" effect="plain">已验证</el-tag>
                        <el-tag v-else size="small" type="info" effect="plain">未验证</el-tag>
                    </span>
                </div>

                <div class="pwd-body">
                    <p class="pwd-desc">
                        交易密码用于下单、查询持仓与出入金。修改成功后需要重新验证才能继续使用。
                    </p>
                    <div class="pwd-row">
                        <span class="lbl">免密时长</span>
                        <el-radio-group :model-value="tradeAuth.onlineHours" size="small">
                            <el-radio-button
                                v-for="h in ([6, 12, 24] as const)"
                                :key="h"
                                :value="h"
                                @click="tradeAuth.onlineHours = h"
                            >
                                {{ h }}h
                            </el-radio-button>
                        </el-radio-group>
                    </div>
                    <div class="pwd-actions">
                        <el-button size="small" type="primary" :disabled="!current" @click="verifyNow">
                            验证交易密码
                        </el-button>
                        <el-button size="small" :disabled="!current" @click="openPwdDialog">
                            修改交易密码
                        </el-button>
                    </div>
                    <div v-if="current" class="pwd-status">
                        <el-icon><InfoFilled /></el-icon>
                        查询持仓、委托和成交前需要先验证交易密码，验证后所选时长内无需重复输入。
                    </div>
                </div>
            </section>
        </div>

        <!-- 银行卡 -->
        <section class="panel bank-panel">
            <div class="panel-title">
                签约银行卡
                <div class="bank-actions">
                    <el-button size="small" text @click="loadCards">
                        <el-icon><Refresh /></el-icon>
                    </el-button>
                    <el-button size="small" type="primary" :disabled="!current" @click="openBindDialog">
                        绑定银行卡
                    </el-button>
                </div>
            </div>

            <div v-if="!current" class="empty-tip">请先开通资金账户</div>
            <div v-else-if="!cards.length && !cardsLoading" class="empty-tip">
                还没有绑定银行卡，绑定后才能入金和出金
            </div>

            <div v-else v-loading="cardsLoading" class="bank-list">
                <div v-for="c in cards" :key="c.id" class="bank-card">
                    <div class="bank-main">
                        <div class="bank-name">{{ c.bankName }}</div>
                        <div class="bank-no num">{{ maskCard(c.cardNumber) }}</div>
                    </div>
                    <div class="bank-meta">
                        <div class="meta-line">预留手机{{ c.phone }}</div>
                        <div class="meta-line">绑定时间{{ fmtDateTime(c.bindTime) }}</div>
                    </div>
                    <el-button size="small" text type="danger" @click="openUnbindDialog(c)">解绑</el-button>
                </div>
            </div>
        </section>

        <!-- 开户弹窗 -->
        <el-dialog v-model="openVisible" title="开通资金账户" width="420px" :close-on-click-modal="false">
            <el-form label-width="82px" @submit.prevent>
                <el-form-item label="交易密码">
                    <el-input
                        v-model="openForm.password"
                        type="password"
                        show-password
                        placeholder="6位以上，用于下单与出金"
                    />
                </el-form-item>
                <el-form-item label="确认密码">
                    <el-input
                        v-model="openForm.confirm"
                        type="password"
                        show-password
                        placeholder="再次输入交易密码"
                    />
                </el-form-item>
            </el-form>
            <div class="dialog-tip">开户后默认只开通「主板」交易权限，其他板块需单独申请。</div>
            <template #footer>
                <el-button @click="openVisible = false">取消</el-button>
                <el-button type="primary" :loading="opening" @click="submitOpenAccount">开通</el-button>
            </template>
        </el-dialog>

        <!-- 开板块弹窗 -->
        <el-dialog v-model="boardVisible" title="开通板块交易权限" width="440px">
            <div v-if="!availableBoards.length" class="dialog-tip">该账户已开通全部可申请板块。</div>
            <el-checkbox-group v-else v-model="boardPicked" class="board-group">
                <el-checkbox v-for="b in availableBoards" :key="b.name" :value="b.name">
                    {{ b.name }}
                    <span class="th">（可用资产需达到{{ b.threshold }}）</span>
                </el-checkbox>
            </el-checkbox-group>
            <template #footer>
                <el-button @click="boardVisible = false">取消</el-button>
                <el-button type="primary" :loading="openingBoard" @click="submitBoard">开通</el-button>
            </template>
        </el-dialog>

        <!-- 修改交易密码 -->
        <el-dialog v-model="pwdVisible" title="修改交易密码" width="420px" :close-on-click-modal="false">
            <el-form label-width="92px" @submit.prevent>
                <el-form-item label="原交易密码">
                    <el-input v-model="pwdForm.oldPassword" type="password" show-password />
                </el-form-item>
                <el-form-item label="新交易密码">
                    <el-input v-model="pwdForm.newPassword" type="password" show-password />
                </el-form-item>
                <el-form-item label="确认新密码">
                    <el-input v-model="pwdForm.confirm" type="password" show-password />
                </el-form-item>
            </el-form>
            <div class="dialog-tip">修改成功后，需要重新验证交易密码才能继续使用。</div>
            <template #footer>
                <el-button @click="pwdVisible = false">取消</el-button>
                <el-button type="primary" :loading="pwdLoading" @click="submitPassword">保存</el-button>
            </template>
        </el-dialog>

        <!-- 绑定银行卡 -->
        <el-dialog v-model="bindVisible" title="绑定银行卡" width="460px" :close-on-click-modal="false">
            <el-form label-width="104px" @submit.prevent>
                <el-form-item label="开户银行">
                    <el-select v-model="bindForm.bankName" style="width: 100%">
                        <el-option v-for="b in BANKS" :key="b" :label="b" :value="b" />
                    </el-select>
                </el-form-item>
                <el-form-item label="银行卡号">
                    <el-input v-model="bindForm.cardNumber" maxlength="19" placeholder="12~19位卡号" />
                </el-form-item>
                <el-form-item label="预留手机号">
                    <el-input v-model="bindForm.phone" maxlength="11" placeholder="银行预留手机号" />
                </el-form-item>
                <el-form-item label="短信验证码">
                    <div class="code-row">
                        <el-input v-model="bindForm.code" maxlength="6" placeholder="6位验证码" />
                        <el-button
                            class="code-btn"
                            :disabled="smsRunning"
                            :loading="verifying"
                            @click="verifyAndSendCode"
                        >
                            {{ smsRunning ? `${smsSeconds}s` : '获取验证码' }}
                        </el-button>
                    </div>
                </el-form-item>
            </el-form>
            <div class="dialog-tip">
                请确认卡号与银行预留手机号一致。点击「校验并获取」后将向该手机号发送验证码。
            </div>
            <template #footer>
                <el-button @click="bindVisible = false">取消</el-button>
                <el-button type="primary" :loading="bindLoading" @click="submitBind">绑定</el-button>
            </template>
        </el-dialog>

        <!-- 解绑银行卡 -->
        <el-dialog v-model="unbindVisible" title="解绑银行卡" width="440px" :close-on-click-modal="false">
            <el-form label-width="104px" @submit.prevent>
                <el-form-item label="银行卡号">
                    <el-input :model-value="maskCard(unbindForm.cardNumber)" disabled />
                </el-form-item>
                <el-form-item label="预留手机号">
                    <el-input v-model="unbindForm.phone" maxlength="11" placeholder="绑卡时使用的手机号" />
                </el-form-item>
                <el-form-item label="短信验证码">
                    <div class="code-row">
                        <el-input v-model="unbindForm.code" maxlength="6" placeholder="6位验证码" />
                        <el-button class="code-btn" :loading="unbindSending" @click="sendUnbindCode">
                            获取验证码
                        </el-button>
                    </div>
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="unbindVisible = false">取消</el-button>
                <el-button type="danger" :loading="unbindLoading" @click="submitUnbind">解绑</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<style scoped>
.assets {
    display: flex;
    flex-direction: column;
    gap: 10px;
    min-height: 100%;
}

/* ------------------------------ 概览 ------------------------------ */

.ov-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 44px;
    padding: 0 14px;
    border-bottom: 1px solid var(--divider);
}

.ov-title {
    font-size: 15px;
    font-weight: 600;
}

.count {
    margin-left: 6px;
    font-size: 13px;
    font-weight: 400;
    color: var(--text-placeholder);
}

.ov-actions {
    display: flex;
    gap: 8px;
}

.acc-list {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(268px, 1fr));
    gap: 10px;
    padding: 12px;
}

.acc-card {
    padding: 12px 14px;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--bg-panel);
    cursor: pointer;
    transition: border-color 0.15s, box-shadow 0.15s;
}

.acc-card:hover {
    border-color: var(--border-strong);
}

.acc-card.active {
    border-color: var(--primary);
    box-shadow: 0 0 0 2px var(--primary-weak);
}

.acc-top {
    display: flex;
    align-items: center;
    justify-content: space-between;
}

.acc-no {
    font-size: 14px;
    color: var(--text-secondary);
}

.acc-money {
    margin-top: 6px;
    font-size: 26px;
    font-weight: 700;
    color: var(--text-primary);
}

.acc-label {
    font-size: 13px;
    color: var(--text-placeholder);
}

.acc-boards {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
    margin-top: 8px;
}

.acc-time {
    margin-top: 8px;
    font-size: 13px;
    color: var(--text-placeholder);
}

/* ------------------------------ 主体 ------------------------------ */

.grid {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    gap: 10px;
}

.money-body {
    padding: 14px;
    display: flex;
    flex-direction: column;
    gap: 12px;
}

.money-tabs {
    display: inline-flex;
    gap: 4px;
    padding: 3px;
    background: var(--bg-hover);
    border-radius: 5px;
    width: fit-content;
}

.money-tab {
    height: 26px;
    padding: 0 18px;
    border: none;
    border-radius: 4px;
    background: transparent;
    font-size: 14px;
    color: var(--text-regular);
    cursor: pointer;
}

.money-tab.active {
    background: var(--bg-panel);
    color: var(--primary);
    font-weight: 600;
    box-shadow: var(--shadow-sm);
}

.amount-row {
    display: flex;
    align-items: center;
    gap: 8px;
}

.amount-row :deep(.el-input-number) {
    flex: 1 1 auto;
}

.lbl {
    font-size: 14px;
    color: var(--text-secondary);
    white-space: nowrap;
}

.unit {
    font-size: 14px;
    color: var(--text-secondary);
}

.quick-amounts {
    display: flex;
    gap: 6px;
}

.quick {
    flex: 1 1 0;
    height: 24px;
    border: 1px solid var(--border);
    border-radius: 3px;
    background: var(--bg-subtle);
    font-size: 13px;
    color: var(--text-regular);
    cursor: pointer;
}

.quick:hover {
    border-color: var(--primary);
    color: var(--primary);
}

.money-tip {
    font-size: 13px;
    line-height: 1.6;
    color: var(--text-placeholder);
}

.money-actions {
    display: flex;
    gap: 8px;
}

.pwd-body {
    padding: 14px;
    display: flex;
    flex-direction: column;
    gap: 12px;
}

.pwd-desc {
    font-size: 14px;
    line-height: 1.7;
    color: var(--text-secondary);
}

.pwd-row {
    display: flex;
    align-items: center;
    gap: 10px;
}

.pwd-actions {
    display: flex;
    gap: 8px;
}

.pwd-status {
    display: flex;
    align-items: flex-start;
    gap: 6px;
    padding: 8px 10px;
    border-radius: 4px;
    background: var(--bg-subtle);
    font-size: 13px;
    line-height: 1.6;
    color: var(--text-secondary);
}

.pwd-status code {
    padding: 0 4px;
    border-radius: 3px;
    background: var(--bg-hover);
    font-family: var(--font-num);
    color: var(--text-primary);
}

/* ------------------------------ 银行卡 ------------------------------ */

.bank-panel {
    padding-bottom: 4px;
}

.bank-actions {
    display: flex;
    gap: 6px;
    align-items: center;
}

.bank-list {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
    gap: 10px;
    padding: 12px;
}

.bank-card {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px 14px;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--bg-subtle);
}

.bank-main {
    flex: 1 1 auto;
    min-width: 0;
}

.bank-name {
    font-size: 15px;
    font-weight: 600;
    color: var(--text-primary);
}

.bank-no {
    margin-top: 2px;
    font-size: 15px;
    letter-spacing: 0.5px;
    color: var(--text-regular);
}

.bank-meta {
    flex: 0 0 auto;
    text-align: right;
}

.meta-line {
    font-size: 13px;
    color: var(--text-placeholder);
    line-height: 1.6;
}

/* ------------------------------ 弹窗 ------------------------------ */

.dialog-tip {
    font-size: 13px;
    line-height: 1.6;
    color: var(--text-placeholder);
}

.board-group {
    display: flex;
    flex-direction: column;
    gap: 6px;
}

.th {
    font-size: 13px;
    color: var(--text-placeholder);
}

.code-row {
    display: flex;
    gap: 8px;
    width: 100%;
}

.code-btn {
    flex: 0 0 112px;
}
</style>
