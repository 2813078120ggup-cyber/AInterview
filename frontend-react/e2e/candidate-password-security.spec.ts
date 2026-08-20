import { expect, test, type BrowserContext, type Page } from '@playwright/test'

test.use({ video: 'off' })

const profile = { id: '11', username: 'candidate_e2e', realName: '双设备候选人', roles: ['CANDIDATE'] }
const transparentPng = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M/wHwAF/gL+3MxZ5wAAAABJRU5ErkJggg=='

async function establishSyntheticSession(context: BrowserContext, accessToken: string, refreshToken: string) {
  await context.addInitScript(({ accessToken, refreshToken, profile }) => {
    if (localStorage.getItem('e2e_session_seeded')) return
    localStorage.setItem('access_token', accessToken)
    localStorage.setItem('refresh_token', refreshToken)
    localStorage.setItem('ai_interview_profile', JSON.stringify(profile))
    localStorage.setItem('e2e_session_seeded', '1')
  }, { accessToken, refreshToken, profile })
}

async function mockWorkspaceShell(page: Page, mockProfile = false) {
  await page.route('**/api/v1/auth/me', route => route.fulfill({ json: { data: profile } }))
  await page.route('**/api/v1/notifications/unread-count', route => route.fulfill({ json: { data: 0 } }))
  await page.route('**/api/v1/notifications**', route => route.fulfill({ json: { data: { records: [], total: 0, pageNo: 1, pageSize: 20 } } }))
  await page.route('**/api/v1/account/sessions', route => route.fulfill({ json: { data: [] } }))
  await page.route('**/api/v1/account/security-events**', route => route.fulfill({
    json: { data: { records: [], total: 0, pageNo: 1, pageSize: 15 } },
  }))
  if (mockProfile) {
    await page.route('**/api/v1/account/profile', route => route.fulfill({
      json: { data: { realName: profile.realName, avatarAvailable: false } },
    }))
  }
}

test('password change rotates device A and rejects device B old access token', async ({ browser, baseURL }) => {
  const deviceA = await browser.newContext({ viewport: { width: 390, height: 844 }, colorScheme: 'light' })
  const deviceB = await browser.newContext({ viewport: { width: 390, height: 844 }, colorScheme: 'dark' })
  await establishSyntheticSession(deviceA, 'access-a-old', 'refresh-a-old')
  await establishSyntheticSession(deviceB, 'access-b-old', 'refresh-b-old')
  await deviceB.addInitScript(() => localStorage.setItem('interviewos-theme', 'dark'))
  const pageA = await deviceA.newPage()
  const pageB = await deviceB.newPage()
  await mockWorkspaceShell(pageA, true)
  await mockWorkspaceShell(pageB)

  await pageA.route('**/api/v1/account/password/change', async route => {
    expect(route.request().postDataJSON()).toEqual({
      currentPassword: 'Current123!',
      newPassword: 'Next12345!',
      refreshToken: 'refresh-a-old',
    })
    await route.fulfill({ json: { data: { accessToken: 'access-a-new', refreshToken: 'refresh-a-new', sessionBehavior: '当前设备已更新登录凭据，其他设备已退出登录' } } })
  })

  await pageB.route('**/api/v1/account/profile', route => {
    expect(route.request().headers().authorization).toBe('Bearer access-b-old')
    return route.fulfill({ status: 401, json: { code: 40100, message: '登录已失效' } })
  })
  await pageB.route('**/api/v1/auth/refresh', route => {
    expect(route.request().postDataJSON()).toEqual({ refreshToken: 'refresh-b-old' })
    return route.fulfill({ status: 401, json: { code: 40100, message: '刷新令牌已失效' } })
  })

  await pageA.goto(`${baseURL}/candidate/settings/security?context=recruitment`)
  await expect(pageA).toHaveURL(/\/candidate\/settings\/security\?context=recruitment$/)
  await expect(pageA.getByRole('heading', { name: '账户设置' })).toBeVisible()
  await pageA.getByLabel('当前密码', { exact: true }).fill('Current123!')
  await pageA.getByLabel('新密码', { exact: true }).fill('Next12345!')
  await pageA.getByLabel('确认新密码', { exact: true }).fill('Next12345!')
  await pageA.getByRole('button', { name: '更新密码' }).click()
  await expect(pageA.getByText('当前设备已更新登录凭据，其他设备已退出登录')).toBeVisible()
  await expect.poll(() => pageA.evaluate(() => localStorage.getItem('access_token'))).toBe('access-a-new')
  await expect.poll(() => pageA.evaluate(() => localStorage.getItem('refresh_token'))).toBe('refresh-a-new')
  expect(await pageA.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)

  await pageB.goto(`${baseURL}/candidate/settings/profile?context=recruitment`)
  await expect(pageB).toHaveURL(/\/login\?next=%2Fcandidate%2Fsettings%2Fprofile%3Fcontext%3Drecruitment$/)
  await expect.poll(() => pageB.evaluate(() => localStorage.getItem('access_token'))).toBeNull()
  await expect.poll(() => pageB.evaluate(() => localStorage.getItem('refresh_token'))).toBeNull()
  await expect(pageB.getByRole('tab', { name: '密码登录', exact: true })).toBeVisible()
  expect(await pageB.evaluate(() => document.documentElement.classList.contains('dark'))).toBe(true)
  expect(await pageB.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)

  await deviceA.close()
  await deviceB.close()
})

test('forgot password keeps new-password inputs on local validation errors', async ({ browser, baseURL }) => {
  const context = await browser.newContext({ viewport: { width: 390, height: 844 } })
  const page = await context.newPage()
  await page.route('**/api/v1/auth/captcha/challenge', route => route.fulfill({
    json: { data: { challengeId: 'reset-captcha', imageDataUrl: transparentPng, expiresInSeconds: 120 } },
  }))
  await page.route('**/api/v1/auth/password/reset/code', route => route.fulfill({
    json: { data: { accepted: true, cooldownSeconds: 60, expiresInSeconds: 300, message: '若该联系方式可用于找回账户，验证码将发送至该联系方式' } },
  }))
  await page.route('**/api/v1/auth/password/reset/verify', route => route.fulfill({
    json: { data: { resetToken: 'reset-ticket', expiresInSeconds: 600 } },
  }))
  await page.goto(`${baseURL}/login`)
  await page.getByRole('button', { name: '忘记密码', exact: true }).click()
  await expect(page.getByRole('heading', { name: '找回密码' })).toBeVisible()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.getByLabel('手机号 / 邮箱').fill('13800000000')
  await page.getByPlaceholder('图形验证码').fill('ABCD')
  await page.getByRole('button', { name: '发送验证码', exact: true }).click()
  await page.getByLabel('短信 / 邮箱验证码').fill('123456')
  await page.getByRole('button', { name: '验证并继续' }).click()
  await page.getByLabel('新密码', { exact: true }).fill('not valid')
  await page.getByLabel('确认新密码', { exact: true }).fill('not valid')
  await page.getByRole('button', { name: '保存新密码' }).click()
  await expect(page.getByRole('alert')).toContainText('新密码须为 8-64 位')
  await expect(page.getByLabel('新密码', { exact: true })).toHaveValue('not valid')
  await expect(page.getByLabel('确认新密码', { exact: true })).toHaveValue('not valid')
  await context.close()
})
