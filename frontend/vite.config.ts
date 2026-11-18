import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// Dev: the browser talks to the Vite server only; /api is proxied to the backend (no CORS needed).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: false },
    },
  },
  build: {
    sourcemap: false,
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    restoreMocks: true,
    css: false,
  },
});
