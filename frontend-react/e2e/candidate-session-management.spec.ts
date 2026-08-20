import { expect, test, type BrowserContext, type Page } from '@playwright/test'

test.use({ video: 'off' })

const profile = { id: '11', username: 'candidate_sessions', realName: '会话候选人', roles: ['CANDIDATE'] }
const now = '2026-08-12T14:30:00'
const sessions = [
  { sessionId: 'session-current', current: true, deviceType: 'DESKTOP', browser: 'Chrome', operatingSystem: 'Windows', maskedIp: '192.168.1.*', createdAt: '2026-08-10T09:00:00', lastActiveAt: now, expiresAt: '2026-09-11T14:30:00' },
  { sessionId: 'session-phone', current: false, deviceType: 'MOBILE', browser: 'Safari', operatingSystem: 'iOS', maskedIp: '2001:db8:1:*', createdAt: '2026-08-08T10:00:00', lastActiveAt: '2026-08-12T12:00:00', expiresAt: '2026-09-07T10:00:00' },
]

async function establishSession(context: BrowserContext, accessToken: string, refreshToken: string) {
  await context.addInitScript(({ accessToken, refreshToken, profile }) => {
    if (localStorage.getItem('e2e_session_seeded')) return
    localStorage.setItem('access_token', accessToken)
    localStorage.setItem('refresh_token', refreshToken)
    localStorage.setItem('ai_interview_profile', JSON.stringify(profile))
    localStorage.setItem('e2e_session_seeded', '1')
  }, { accessToken, refreshToken, profile })
}

async function mockWorkspace(page: Page, mockProfile = false) {
  await page.route('**/api/v1/auth/me', route => route.fulfill({ json: { data: profile } }))
  await page.route('**/api/v1/notifications/unread-count', route => route.fulfill({ json: { data: 0 } }))
  await page.route('**/api/v1/notifications**', route => route.fulfill({ json: { data: { records: [], total: 0, pageNo: 1, pageSize: 20 } } }))
  await page.route('**/api/v1/account/security-events**', route => route.fulfill({
    json: { data: { records: [], total: 0, pageNo: 1, pageSize: 15 } },
  }))
  if (mockProfile) {
    await page.route('**/api/v1/account/profile', route => route.fulfill({
      json: { data: { realName: profile.realName, avatarAvailable: false } },
    }))
  }
}

test('two devices show current first and revoke other devices without clearing current session', async ({ browser, baseURL }) => {
  const deviceA = await browser.newContext({ viewport: { width: 390, height: 844 }, colorScheme: 'light' })
  const deviceB = await browser.newContext({ viewport: { width: 1024, height: 768 }, colorScheme: 'dark' })
  await establishSession(deviceA, 'access-a', 'refresh-a')
  await establishSession(deviceB, 'access-b', 'refresh-b')
  const pageA = await deviceA.newPage()
  const pageB = await deviceB.newPage()
  await mockWorkspace(pageA, true)
  await mockWorkspace(pageB)

  let sessionLoads = 0
  await pageA.route('**/api/v1/account/sessions', route => {
    sessionLoads += 1
    return route.fulfill({ json: { data: sessions } })
  })
  await pageA.route('**/api/v1/account/sessions/others', route => {
    expect(route.request().method()).toBe('DELETE')
    expect(route.request().headers().authorization).toBe('Bearer access-a')
    return route.fulfill({ json: { data: null } })
  })
  await pageB.route('**/api/v1/account/profile', route => route.fulfill({ status: 401, json: { code: 40100, message: '登录已失效' } }))
  await pageB.route('**/api/v1/auth/refresh', route => {
    expect(route.request().postDataJSON()).toEqual({ refreshToken: 'refresh-b' })
    return route.fulfill({ status: 401, json: { code: 40100, message: '刷新令牌已失效' } })
  })

  await pageA.goto(`${baseURL}/candidate/settings/security?context=recruitment`)
  const cards = pageA.getByRole('article')
  await expect(cards).toHaveCount(2)
  await expect(cards.first().getByText('当前设备', { exact: true })).toBeVisible()
  await expect(cards.first()).toContainText('Chrome · Windows')
  await expect(cards.nth(1)).toContainText('Safari · iOS')
  await expect(pageA.getByText('192.168.1.*')).toBeVisible()
  await expect(pageA.getByText('另有 1 台设备保持登录，可一次性退出。')).toBeVisible()
  await pageA.waitForTimeout(350)
  expect(sessionLoads).toBe(1)
  for (const viewport of [{ width: 768, height: 900 }, { width: 1440, height: 900 }]) {
    await pageA.setViewportSize(viewport)
    expect(await pageA.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  }

  await pageA.getByRole('button', { name: '退出其他设备' }).click()
  await expect(pageA.getByRole('dialog')).toContainText('当前设备会继续登录')
  await pageA.getByRole('button', { name: '确认退出' }).press('Enter')
  await expect(pageA.getByText('其他设备已退出登录。')).toBeVisible()
  await expect(pageA.getByRole('article')).toHaveCount(1)
  await expect.poll(() => pageA.evaluate(() => localStorage.getItem('access_token'))).toBe('access-a')
  expect(await pageA.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)

  await pageB.goto(`${baseURL}/candidate/settings/profile`)
  await expect(pageB).toHaveURL(/\/login\?next=%2Fcandidate%2Fsettings%2Fprofile$/)
  await expect.poll(() => pageB.evaluate(() => localStorage.getItem('access_token'))).toBeNull()
  await expect.poll(() => pageB.evaluate(() => localStorage.getItem('refresh_token'))).toBeNull()
  expect(await pageB.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)

  await deviceA.close()
  await deviceB.close()
})

test('revoking current device clears local credentials and failed revoke keeps list', async ({ browser, baseURL }) => {
  const context = await browser.newContext({ viewport: { width: 390, height: 844 } })
  await establishSession(context, 'access-current', 'refresh-current')
  const page = await context.newPage()
  await mockWorkspace(page, true)
  await page.route('**/api/v1/account/sessions', route => route.fulfill({ json: { data: sessions } }))
  let attempts = 0
  await page.route('**/api/v1/account/sessions/session-current', route => {
    attempts += 1
    return attempts === 1
      ? route.fulfill({ status: 503, json: { code: 50300, message: '会话服务暂时不可用' } })
      : route.fulfill({ json: { data: null } })
  })

  await page.goto(`${baseURL}/candidate/settings/security`)
  await expect(page.getByRole('article')).toHaveCount(2)
  await page.getByRole('button', { name: '退出当前设备' }).click()
  await page.getByRole('button', { name: '确认退出' }).click()
  await expect(page.getByText('会话服务暂时不可用')).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('article')).toHaveCount(2)

  await page.getByRole('button', { name: '退出当前设备' }).click()
  await page.getByRole('button', { name: '确认退出' }).click()
  await expect(page).toHaveURL(url => url.pathname === '/login' && (url.search === '' || url.search === '?next=%2Fcandidate%2Fsettings%2Fsecurity'))
  await expect.poll(() => page.evaluate(() => localStorage.getItem('access_token'))).toBeNull()
  await expect.poll(() => page.evaluate(() => localStorage.getItem('refresh_token'))).toBeNull()

  await context.close()
})
