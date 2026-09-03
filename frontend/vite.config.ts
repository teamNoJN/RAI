import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), vueDevTools()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    // FE 는 게이트웨이 하나만 본다 (backend/gateway).
    // 이 머신은 8080 이 점유돼 있어 게이트웨이 local 프로필이 18080 을 쓴다
    // (backend/gateway/src/main/resources/application-local.yml 참고).
    // 프록시를 쓰므로 CORS 를 타지 않고, VITE_API_BASE_URL 은 비워 둔다.
    proxy: {
      '/api': {
        target: process.env.VITE_PROXY_TARGET ?? 'http://localhost:18080',
        changeOrigin: true,
      },
    },
  },
})
