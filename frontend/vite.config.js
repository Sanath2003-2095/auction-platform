import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In development the API and WebSocket are proxied to the Spring Boot server,
// so the browser sees a single origin and CORS never gets in the way.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5174,
    strictPort: true,
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
});
