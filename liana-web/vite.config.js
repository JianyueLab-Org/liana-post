import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import tailwindcss from '@tailwindcss/vite';
import { fileURLToPath, URL } from 'node:url';

const gatewayTarget = process.env.VITE_GATEWAY_TARGET || 'http://127.0.0.1:8080';
const servicePrefixes = ['/liana-auth-service', '/liana-facility-service', '/liana-oms-service', '/liana-dispatch-service', '/liana-sorting-service', '/liana-records-service', '/liana-transport-service'];

export default defineConfig({
  plugins: [tailwindcss(), vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    host: true,
    proxy: Object.fromEntries(servicePrefixes.map((prefix) => [prefix, { target: gatewayTarget, changeOrigin: true, secure: false }])),
  },
});
