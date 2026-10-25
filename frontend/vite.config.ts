import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 后端没有 CORS 配置，开发环境一律走 Vite 代理：/api -> http://localhost:8080（去掉 /api 前缀）
const BACKEND = 'http://localhost:8080'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    // 编辑器 / 工具做原子写入时会先建 `.<name>.<uuid>.tmpdir` 之类的临时目录，
    // 被 watcher 扫到会在 Windows 上抛 EBUSY 直接把 dev server 打挂，这里忽略掉
    watch: {
      ignored: ['**/node_modules/**', '**/dist/**', '**/.*.tmpdir/**', '**/*.tmp']
    },
    proxy: {
      '/api': {
        target: BACKEND,
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      },
      '/ws': {
        target: BACKEND,
        ws: true,
        changeOrigin: true
      }
    }
  },
  build: {
    chunkSizeWarningLimit: 1500
  }
})
