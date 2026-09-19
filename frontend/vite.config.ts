import { defineConfig, loadEnv, type ProxyOptions } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

/** v0.0.4 🍊 Proxies /api to the Spring Boot backend without buffering or timing out the SSE stream. */
const apiProxy: ProxyOptions = {
  target: 'http://localhost:8080',
  changeOrigin: true,
  configure(proxy) {
    proxy.on('proxyReq', (proxyReq, req) => {
      if (req.url?.startsWith('/api/stream')) {
        // A compressed event stream would be buffered by the encoder: always ask for identity.
        proxyReq.setHeader('Accept-Encoding', 'identity');
      }
    });
    proxy.on('proxyRes', (proxyRes, req) => {
      if (String(proxyRes.headers['content-type'] ?? '').includes('text/event-stream')) {
        proxyRes.headers['cache-control'] = 'no-cache, no-transform';
        proxyRes.headers['x-accel-buffering'] = 'no';
        req.socket.setNoDelay(true);
        req.socket.setTimeout(0);
      }
    });
  },
};

/** v0.0.4 🍊 Vite config: React + Tailwind v4, the /api proxy and the `mock` mode (VITE_MOCK=1). */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_');
  const mock = mode === 'mock' || env.VITE_MOCK === '1';
  return {
    plugins: [react(), tailwindcss()],
    define: mock ? { 'import.meta.env.VITE_MOCK': JSON.stringify('1') } : {},
    server: {
      port: 5173,
      proxy: { '/api': apiProxy },
    },
    preview: {
      port: 4173,
      proxy: { '/api': apiProxy },
    },
  };
});
