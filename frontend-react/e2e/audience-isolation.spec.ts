import { expect, test, type Page } from '@playwright/test'

test.use({ video: 'off' })

type TestProfile = {
  id: string
  username: string
  realName: string
  roles: string[]
}

const cachedProfile: TestProfile = {
  id: 'cached-admin',
  username: 'cached-admin',
  realName: '本地缓存伪造管理员',
  roles: ['ADMIN'],
}

const serverProfile: TestProfile = {
  id: 'server-candidate',
  username: 'server-candidate',
  realName: '服务端候选人',
  roles: ['CANDIDATE'],
}

async function mockSession(page: Page, authoritativeProfile: TestProfile, cached = cachedProfile) {
  await page.addInitScript(({ profile }) => {
    localStorage.setItem('access_token', 'e2e-audience-token')
    localStorage.setItem('refresh_token', 'e2e-audience-refresh')
    localStorage.setItem('ai_interview_profile', JSON.stringify(profile))
  }, { profile: cached })
  await page.route('**/api/**', route => route.fulfill({ json: { data: [] } }))
  await page.route('**/v1/auth/me', route => route.fulfill({ json: { data: authoritativeProfile } }))
}

async function expectPath(page: Page, pathname: string) {
  await expect.poll(() => new URL(page.url()).pathname).toBe(pathname)
}

test('server-confirmed audience wins over forged local role and isolates admin routes', async ({ page }) => {
  await page.addInitScript(({ profile }) => {
    localStorage.setItem('access_token', 'e2e-audience-token')
    localStorage.setItem('refresh_token', 'e2e-audience-refresh')
    localStorage.setItem('ai_interview_profile', JSON.stringify(profile))
  }, { profile: cachedProfile })

  await page.route('**/api/**', route => route.fulfill({ json: { data: [] } }))
  await page.route('**/v1/auth/me', route => route.fulfill({ json: { data: serverProfile } }))

  await page.goto('/admin/workspace')
  await expect(page).toHaveURL(/\/workspace$/)
  await expect(page).not.toHaveURL(/\/admin(?:\/|$)/)
  await expect.poll(() => page.evaluate(() => JSON.parse(localStorage.getItem('ai_interview_profile') || '{}').roles)).toEqual(['CANDIDATE'])

  await page.goto('/candidate/settings/profile')
  await expect(page).toHaveURL(/\/candidate\/settings\/profile$/)

  await page.goto('/admin/ai-governance')
  await expect(page).toHaveURL(/\/workspace$/)

  await page.goto('/jobs')
  await expect(page).toHaveURL(/\/jobs$/)
})

test('platform admin auxiliary roles remain in the admin workspace', async ({ page }) => {
  const cachedCandidate = { ...serverProfile, roles: ['CANDIDATE'] }
  const serverAdmin = { id: 'platform-admin', username: 'platform-admin', realName: '平台管理员', roles: ['ADMIN', 'HR'] }
  await page.addInitScript(({ profile }) => {
    localStorage.setItem('access_token', 'e2e-admin-token')
    localStorage.setItem('refresh_token', 'e2e-admin-refresh')
    localStorage.setItem('ai_interview_profile', JSON.stringify(profile))
  }, { profile: cachedCandidate })

  await page.route('**/api/**', route => route.fulfill({ json: { data: [] } }))
  await page.route('**/v1/auth/me', route => route.fulfill({ json: { data: serverAdmin } }))

  await page.goto('/workspace')
  await expect(page).toHaveURL(/\/admin\/workspace$/)
  await page.goto('/jobs')
  await expect(page).toHaveURL(/\/admin\/workspace$/)
})

test('candidate routes cannot enter company or admin workspaces', async ({ page }) => {
  await mockSession(page, serverProfile)

  for (const source of ['/admin/workspace', '/company', '/company/positions']) {
    await page.goto(source)
    await expectPath(page, '/workspace')
  }
})

test('company routes cannot enter candidate or admin workspaces', async ({ page }) => {
  const companyProfile: TestProfile = {
    id: 'company-recruiter',
    username: 'company-recruiter',
    realName: '企业招聘专员',
    roles: ['COMPANY_RECRUITER'],
  }
  await mockSession(page, companyProfile, serverProfile)

  for (const source of ['/workspace', '/jobs', '/admin/workspace', '/admin/ai-governance']) {
    await page.goto(source)
    await expectPath(page, '/company')
  }
})

test('admin routes cannot enter candidate or company workspaces', async ({ page }) => {
  const adminProfile: TestProfile = {
    id: 'platform-admin',
    username: 'platform-admin',
    realName: '平台管理员',
    roles: ['ADMIN'],
  }
  await mockSession(page, adminProfile, serverProfile)

  for (const source of ['/workspace', '/jobs', '/candidate/settings/profile', '/company', '/company/positions']) {
    await page.goto(source)
    await expectPath(page, '/admin/workspace')
  }
})

test('unsupported and cross-domain roles fail closed without a workspace', async ({ browser }) => {
  const invalidRoleSets = [
    ['HR'],
    ['INTERVIEWER'],
    [],
    ['UNKNOWN_PLATFORM_ROLE'],
    ['CANDIDATE', 'COMPANY_RECRUITER'],
    ['ADMIN', 'CANDIDATE'],
    ['ADMIN', 'COMPANY_ADMIN'],
    ['COMPANY_RECRUITER', 'HR'],
  ]

  for (const roles of invalidRoleSets) {
    const context = await browser.newContext()
    const page = await context.newPage()
    await mockSession(page, {
      id: `invalid-${roles.join('-') || 'empty'}`,
      username: 'invalid-role-user',
      realName: '非法角色账号',
      roles,
    })

    await page.goto('/workspace')
    await expect(page.getByText('无可用工作区', { exact: true })).toBeVisible()
    await expectPath(page, '/workspace')
    await expect(page).not.toHaveURL(/\/admin(?:\/|$)/)
    await expect(page).not.toHaveURL(/\/company(?:\/|$)/)

    await page.goto('/login?next=%2Fadmin%2Fworkspace')
    await expect(page.getByText('无可用工作区', { exact: true })).toBeVisible()
    await expectPath(page, '/login')
    await context.close()
  }
})
