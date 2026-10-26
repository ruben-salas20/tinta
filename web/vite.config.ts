import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';

// En desarrollo, TINTA_DEV_IP elige dónde escucha (por ejemplo, la IP de
// Tailscale para abrirlo desde otro equipo). Por defecto, solo esta máquina.
const ip = process.env.TINTA_DEV_IP ?? '127.0.0.1';

export default defineConfig({
  plugins: [svelte()],
  server: {
    host: ip,
    port: 8092,
    proxy: { '/api': `http://${ip}:8091`, '/health': `http://${ip}:8091` },
  },
  test: { include: ['src/**/*.test.ts'] },
});
