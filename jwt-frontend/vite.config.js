import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
    }),
    Components({
      resolvers: [ElementPlusResolver()],
    }),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  server: {
    proxy: {
      '/api': {
        // 后端固定跑在 8081：8080 会被 Steam 的 CEF 调试端口占用
        target: 'http://localhost:8081',
        changeOrigin: true,
        // SSE（/api/ai/chat/stream）走这条代理时，必须确保响应不被缓冲，
        // 否则内容会攒在中间层一起发，前端就看不到"逐字出现"的效果
        configure: (proxy) => {
          proxy.on('proxyRes', (proxyRes) => {
            const type = proxyRes.headers['content-type'] || ''
            if (type.includes('text/event-stream')) {
              // 告诉中间层：这是一条流，别缓存、别攒
              proxyRes.headers['cache-control'] = 'no-cache, no-transform'
              proxyRes.headers['x-accel-buffering'] = 'no'
            }
          })
        },
      },
    },
  },
})
