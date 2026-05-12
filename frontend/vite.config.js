import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { configDefaults } from 'vitest/config'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.js'],
    exclude: [...configDefaults.exclude, 'src/components/SocialAuthButtons.test.jsx'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html'],
      reportsDirectory: './coverage',
      include: ['src/**/*.{js,jsx}'],
      exclude: [
        '**/node_modules/**',
        'src/test/**',
        '**/*.test.{js,jsx}',
        'src/components/SocialAuthButtons.test.jsx',
        // OAuth UI disabled in prod (`return null`); excluding avoids skewing totals.
        'src/components/SocialAuthButtons.jsx',
      ],
      // Team/issue baseline: enforce minimums without excluding large UI modules from the report.
      thresholds: {
        lines: 80,
        statements: 78,
        branches: 70,
        functions: 77,
      },
    },
  },
})
