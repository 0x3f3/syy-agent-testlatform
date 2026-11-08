import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    host: true,
    proxy: {
      '/api': {
        // The browser talks to Vite on the same origin; Vite forwards to Spring Boot.
        target: 'http://127.0.0.1:8081',
        changeOrigin: true,
        // A same-origin Vite proxy request does not need a browser Origin header.
        // Removing it prevents Spring from rejecting a proxied request as an invalid CORS request.
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => {
            proxyReq.removeHeader('origin')
          })
        }
      }
    }
  }
})
