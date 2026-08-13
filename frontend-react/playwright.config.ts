import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.spec.ts',
  timeout: 90_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    reducedMotion: 'reduce',
    colorScheme: 'light',
  },
  projects: [{
    name: 'chromium',
    use: {
      browserName: 'chromium',
      launchOptions: { executablePath: process.env.E2E_CHROMIUM_PATH ?? 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe' },
      viewport: { width: 1440, height: 900 },
    },
  }],
})
