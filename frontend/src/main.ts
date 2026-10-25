import { createApp, type Component } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIcons from '@element-plus/icons-vue'

// Element Plus样式先行加载，主题覆盖文件随后加载
import 'element-plus/dist/index.css'
import './styles/theme.css'
import './styles/element.css'

import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })

// 注册Element Plus图标为全局组件
for (const [name, component] of Object.entries(ElementPlusIcons)) {
    app.component(name, component as Component)
}

// 恢复登录状态并建立行情连接，不阻塞首屏渲染
void useAuthStore(pinia).init()

app.mount('#app')
