import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// Los tests de fechas (vencimientos, sprints, cambio de hora de octubre) dependen de
// la zona horaria: se fija la de la aplicacion para que den lo mismo en cualquier maquina.
process.env.TZ = 'Europe/Madrid'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': new URL('./src', import.meta.url).pathname },
  },
  server: {
    port: 5173,
    // El frontend llama a /api/... y Vite lo reenvia al backend: asi no hay CORS
    // en desarrollo y la URL es la misma que en produccion (Nginx).
    proxy: {
      '/api': {
        target: process.env.VITE_PROXY_TARGET ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    coverage: {
      provider: 'v8',
      include: ['src/**'],
      // Fuera solo lo que no es codigo de la aplicacion: los propios tests y sus ayudas, el
      // arranque (main.tsx, que solo monta <App/> en el DOM) y los ficheros de solo tipos.
      exclude: ['src/**/*.test.{ts,tsx}', 'src/test/**', 'src/main.tsx', 'src/types/**', 'src/**/*.d.ts'],
      reporter: ['text-summary', 'html'],
      // Regla del proyecto, la misma que JaCoCo en el backend: si cualquiera de las cuatro
      // metricas baja del 80 %, `npm run coverage` (y por tanto el QA) falla.
      thresholds: { lines: 80, statements: 80, functions: 80, branches: 80 },
    },
  },
})
