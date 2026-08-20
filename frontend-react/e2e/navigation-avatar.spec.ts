import { expect, test, type Page } from '@playwright/test'

test.use({ video: 'off' })

type MockUser = {
  id: string
  username: string
  realName: string
  roles: string[]
  companyId?: string
  avatarAvailable?: boolean
}

const transparentPng = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M/wHwAF/gL+3MxZ5wAAAABJRU5ErkJggg==',
  'base64',
)
const cachedAvatar = `data:image/png;base64,${transparentPng.toString('base64')}`

async function mockLogin(page: Page, user: MockUser) {
  await page.route('**/api/**', route => route.fulfill({
    status: 503,
    json: { code: 50300, message: '页面数据测试桩未启用' },
  }))
  await page.route('**/api/v1/auth/me', route => route.fulfill({ json: { data: user } }))
  await page.route('**/api/v1/auth/captcha/challenge', route => route.fulfill({ json: { data: { challengeId: 'captcha-e2e', imageDataUrl: cachedAvatar, expiresInSeconds: 120 } } }))
  await page.route('**/api/v1/auth/login', route => {
    const body = route.request().postDataJSON() as { username: string; password: string; captchaChallengeId?: string; captchaCode?: string }
    expect(body.captchaChallengeId).toBe('captcha-e2e')
    expect(body.captchaCode).toBe('ABCD')
    return route.fulfill({ json: { data: { token: 'access-e2e', refreshToken: 'refresh-e2e', user } } })
  })
  await page.route('**/api/v1/account/profile', route => route.fulfill({
    json: { data: { realName: user.realName, avatarAvailable: Boolean(user.avatarAvailable) } },
  }))
  await page.route('**/api/v1/notifications**', route => route.fulfill({
    status: 503,
    json: { code: 50300, message: '通知测试桩未启用' },
  }))
}

async function login(page: Page, username: string) {
  const identifier = username.includes('@') ? username : `${username}@e2e.test`
  await page.getByLabel('用户名 / 手机号 / 邮箱').fill(identifier)
  await page.getByPlaceholder('密码').fill('Password123')
  await page.getByPlaceholder('图形验证码').fill('ABCD')
  await page.getByRole('button', { name: '登录', exact: true }).click()
}

test('features platform entry and job entry preserve different destinations', async ({ page }) => {
  await mockLogin(page, { id: '11', username: 'candidate_e2e', realName: '候选人', roles: ['CANDIDATE'] })
  await page.goto('/features')
  await page.getByRole('button', { name: '进入平台' }).first().click()
  await expect(page).toHaveURL(/\/login$/)

  await page.goto('/features')
  await page.getByRole('button', { name: '查看招聘岗位' }).click()
  await expect(page).toHaveURL(/\/login\?next=%2Fjobs$/)

  await login(page, 'candidate_e2e@example.test')
  await expect(page).toHaveURL(/\/jobs$/)
})

for (const scenario of [
  { username: 'candidate_e2e', roles: ['CANDIDATE'], home: '/workspace', nav: '候选人端业务域' },
  { username: 'company_e2e', roles: ['COMPANY_RECRUITER'], companyId: '21', home: '/company', nav: '企业端业务域' },
  { username: 'admin_e2e', roles: ['ADMIN'], home: '/admin/workspace', nav: '超级管理员端业务域' },
] as const) {
  test(`${scenario.username} logs into the first top-level navigation destination`, async ({ page }) => {
    await mockLogin(page, {
      id: scenario.username,
      username: scenario.username,
      realName: scenario.username,
      roles: [...scenario.roles],
      companyId: 'companyId' in scenario ? scenario.companyId : undefined,
    })
    await page.goto('/login')
    await login(page, scenario.username)
    await expect(page).toHaveURL(url => url.pathname === scenario.home)
    const firstTopLevelLink = page.getByRole('navigation', { name: scenario.nav }).getByRole('link').first()
    await expect(firstTopLevelLink).toHaveAttribute('href', scenario.home)
    await expect(firstTopLevelLink).toHaveAttribute('aria-current', 'page')
  })
}

test('cached avatar is visible on the first frame after reload', async ({ context, page }) => {
  const user = { id: 'avatar-user', username: 'avatar_user', realName: '头像用户', roles: ['CANDIDATE'], avatarAvailable: true }
  await context.addInitScript(({ user, cachedAvatar }) => {
    localStorage.setItem('access_token', 'access-avatar')
    localStorage.setItem('refresh_token', 'refresh-avatar')
    localStorage.setItem('ai_interview_profile', JSON.stringify(user))
    localStorage.setItem(`ai-interview-avatar:v1:${user.id}`, cachedAvatar)
  }, { user, cachedAvatar })
  await page.route('**/api/**', route => route.fulfill({ status: 503, json: { code: 50300, message: '测试桩未启用' } }))
  await page.route('**/api/v1/auth/me', route => route.fulfill({ json: { data: user } }))
  await page.route('**/api/v1/account/profile', async route => {
    await new Promise(resolve => setTimeout(resolve, 500))
    await route.fulfill({ json: { data: { realName: user.realName, avatarAvailable: true } } })
  })
  await page.route('**/api/v1/account/avatar/content', async route => {
    await new Promise(resolve => setTimeout(resolve, 2_500))
    await route.fulfill({ contentType: 'image/png', body: transparentPng })
  })

  await page.goto('/workspace')
  const avatar = page.getByRole('button', { name: '打开用户菜单' }).locator('img')
  await expect(avatar).toHaveAttribute('src', /^data:image\//, { timeout: 1_000 })

  await page.reload()
  await expect(avatar).toHaveAttribute('src', /^data:image\//, { timeout: 1_000 })
  await expect.poll(() => page.evaluate(userId => localStorage.getItem(`ai-interview-avatar:v1:${userId}`), user.id)).not.toBeNull()
})

test('admin operations owns platform settings and service tickets', async ({ page }) => {
  await mockLogin(page, { id: 'admin-navigation', username: 'admin_e2e', realName: '管理端', roles: ['ADMIN'] })
  await page.goto('/login')
  await login(page, 'admin_e2e')

  await page.getByRole('navigation', { name: '超级管理员端业务域' }).getByRole('link', { name: '运维' }).click()
  const operationsNavigation = page.getByRole('navigation', { name: '运维页面' })
  await expect(operationsNavigation.getByRole('link')).toHaveCount(4)
  await expect(operationsNavigation.getByRole('link', { name: /运行状态/ })).toBeVisible()
  await expect(operationsNavigation.getByRole('link', { name: /平台设置/ })).toBeVisible()
  await expect(operationsNavigation.getByRole('link', { name: /服务工单/ })).toBeVisible()
  await expect(operationsNavigation.getByRole('link', { name: /操作审计/ })).toBeVisible()
})

test('provider test reports progress and result in the provider card', async ({ page }) => {
  await mockLogin(page, { id: 'admin-provider', username: 'admin_e2e', realName: '管理端', roles: ['ADMIN'] })
  await page.route('**/api/v1/admin/ai-providers', route => route.fulfill({
    json: { data: [{
      id: 'provider-1', name: 'OpenTalking 开源虚拟人', code: 'open-talking-virtual-human', kind: 'virtual-human',
      baseUrl: '/opentalking', chatModel: 'mock', voiceModel: 'zh-CN-XiaoxiaoNeural', avatarModel: 'dogo-light2d',
      apiKey: '', apiSecret: '', appId: 'edge', enabled: true, textDefault: false, voiceDefault: true, remark: 'E2E Provider',
    }] },
  }))
  await page.route('**/api/v1/admin/ai-providers/provider-1/test', async route => {
    await new Promise(resolve => setTimeout(resolve, 250))
    await route.fulfill({ json: { data: { success: true, state: 'SUCCESS', statusCode: 200, latencyMs: 18, message: 'OpenTalking 服务可用' } } })
  })
  await page.goto('/login')
  await login(page, 'admin_e2e')
  await page.goto('/admin/settings')

  const providerCard = page.locator('section').filter({ hasText: 'open-talking-virtual-human' }).last()
  await providerCard.getByRole('button', { name: '测试', exact: true }).click()
  await expect(providerCard.getByRole('button', { name: '测试中…', exact: true })).toBeVisible()
  await expect(providerCard.getByTestId('provider-test-status')).toContainText('Provider 测试成功')
  await expect(providerCard.getByTestId('provider-test-status')).toContainText('HTTP 200 · 18 ms')
})
