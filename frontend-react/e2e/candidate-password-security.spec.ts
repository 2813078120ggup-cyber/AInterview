import { expect, test, type BrowserContext, type Page } from '@playwright/test'

test.use({ video: 'off' })

const profile = { id: '11', username: 'candidate_e2e', realName: '双设备候选人', roles: ['CANDIDATE'] }

async function establishSyntheticSession(context: BrowserContext, accessToken: string, refreshToken: string) {
  await context.addInitScript(({ accessToken, refreshToken, profile }) => {
    localStorage.setItem('access_token', accessToken)
    localStorage.setItem('refresh_token', refreshToken)
    localStorage.setItem('ai_interview_profile', JSON.stringify(profile))
  }, { accessToken, refreshToken, profile })
}

async function mockWorkspaceShell(page: Page) {
  await page.route('**/api/v1/notifications/unread-count', route => route.fulfill({ json: { data: 0 } }))
  await page.route('**/api/v1/notifications**', route => route.fulfill({ json: { data: { records: [], total: 0, pageNo: 1, pageSize: 20 } } }))
  await page.route('**/api/v1/account/sessions', route => route.fulfill({ json: { data: [] } }))
}

test('password change rotates device A and rejects device B old access token', async ({ browser, baseURL }) => {
  const deviceA = await browser.newContext({ viewport: { width: 390, height: 844 }, colorScheme: 'light' })
  const deviceB = await browser.newContext({ viewport: { width: 390, height: 844 }, colorScheme: 'dark' })
  await establishSyntheticSession(deviceA, 'access-a-old', 'refresh-a-old')
  await establishSyntheticSession(deviceB, 'access-b-old', 'refresh-b-old')
  await deviceB.addInitScript(() => localStorage.setItem('interviewos-theme', 'dark'))
  const pageA = await deviceA.newPage()
  const pageB = await deviceB.newPage()
  await mockWorkspaceShell(pageA)
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
  await expect(pageB.getByText('账户资料暂时不可用')).toBeVisible()
  await expect(pageB.getByText('登录已失效')).toBeVisible()
  expect(await pageB.evaluate(() => document.documentElement.classList.contains('dark'))).toBe(true)
  expect(await pageB.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)

  await deviceA.close()
  await deviceB.close()
})

test('forgot password dialog keeps non-sensitive inputs on validation errors', async ({ browser, baseURL }) => {
  const context = await browser.newContext({ viewport: { width: 390, height: 844 } })
  const page = await context.newPage()
  await page.goto(`${baseURL}/login`)
  await page.getByRole('button', { name: '忘记密码？' }).click()
  await page.getByLabel('已验证手机号').fill('13800000000')
  await page.getByLabel('验证码').fill('123456')
  await page.locator('#reset-password').fill('not valid')
  await page.locator('#reset-confirm-password').fill('not valid')
  await page.getByRole('button', { name: '重置密码' }).click()
  await expect(page.getByRole('alert')).toContainText('新密码不符合规则')
  await expect(page.getByLabel('已验证手机号')).toHaveValue('13800000000')
  await expect(page.getByLabel('验证码')).toHaveValue('123456')
  await expect(page.locator('#reset-password')).toHaveValue('not valid')
  await context.close()
})
