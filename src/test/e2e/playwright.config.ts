import { defineConfig, devices } from '@playwright/test';

/**
 * Configuração Playwright para o painel admin do T-Mill.
 *
 * Assume que a aplicação está rodando em http://localhost:8082
 * (veja `bootRun` no build.gradle). Para CI, use um serviço
 * separado no GitHub Actions.
 */
export default defineConfig({
  testDir: './specs',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: process.env.CI ? 'github' : 'html',

  use: {
    baseURL: process.env.ADMIN_BASE_URL || 'http://localhost:8082',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    // Mobile coverage (Fase 2a)
    {
      name: 'mobile-chrome',
      use: { ...devices['Pixel 5'] },
    },
  ],
});