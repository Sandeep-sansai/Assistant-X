import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In development the page runs on :5173 and forwards API calls to Spring Boot on :8080.
// `npm run build` writes the finished page into Spring Boot's static folder, so the backend serves it.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080' },
  },
  build: {
    outDir: process.env.OUT_DIR || '../backend/src/main/resources/static',
    emptyOutDir: true,
  },
});
