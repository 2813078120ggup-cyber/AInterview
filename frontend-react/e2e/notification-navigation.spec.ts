import { expect, test, type Page } from '@playwright/test'

test.use({ video: 'off' })

type Scenario = {
  name: string
  roles: string[]
  home: string
  destination: string
}

async function prepare(page: Page, scenario: Scenario) {
  const user = { id: `${scenario.name}-user`, username: scenario.name, realName: scenario.name, roles: scenario.roles, companyId: scenario.roles.some(role => role.startsWith('COMPANY_')) ? '21' : undefined }
  await page.addInitScript(userValue => {
    localStorage.setItem('access_token', 'notification-access')
    localStorage.setItem('refresh_token', 'notification-refresh')
    localStorage.setItem('ai_interview_profile', JSON.stringify(userValue))
  }, user)
  await page.route('**/api/**', route => route.fulfill({ status: 503, json: { code: 50300, message: '页面数据测试桩未启用' } }))
  await page.route('**/api/v1/auth/me', route => route.fulfill({ json: { data: user } }))
  await page.route('**/api/v1/account/profile', route => route.fulfill({ json: { data: { realName: user.realName, avatarAvailable: false } } }))
  await page.route('**/api/v1/notifications?*', route => route.fulfill({ json: { data: { records: [{
    id: '91', notificationType: 'JOB_APPLICATION', title: '申请状态已更新', content: '点击查看对应业务详情',
    businessType: 'JOB_APPLICATION', businessId: '501', actionPath: scenario.destination, read: false, createdAt: '2026-08-13T21:00:00',
  }], total: 1, pageNo: 1, pageSize: 50 } } }))
  await page.route('**/api/v1/notifications/91/read', route => route.fulfill({ json: { data: null } }))
  if (scenario.roles.includes('CANDIDATE')) {
    await page.route('**/api/v1/recruitment/applications/501', route => route.fulfill({ json: { data: {
      id: '501', applicationNo: 'APP-NOTIFICATION-501', companyName: '通知测试企业', positionName: '通知测试岗位',
      status: 'SUBMITTED', matchStatus: 'MANUAL', history: [], submittedAt: '2026-08-13T20:00:00', updatedAt: '2026-08-13T21:00:00',
    } } }))
  }
}

for (const scenario of [
  { name: '候选人', roles: ['CANDIDATE'], home: '/workspace', destination: '/applications?applicationId=501' },
  { name: '企业端', roles: ['COMPANY_RECRUITER'], home: '/company', destination: '/company/applications/501' },
  { name: '管理端', roles: ['ADMIN'], home: '/admin/workspace', destination: '/admin/recruitment/applications/501' },
] satisfies Scenario[]) {
  test(`${scenario.name}点击站内通知进入对应业务页面`, async ({ page }) => {
    await prepare(page, scenario)
    await page.goto(scenario.home)
    await page.getByRole('button', { name: '通知' }).click()
    await page.getByRole('button', { name: /未读通知：申请状态已更新/ }).click()
    await expect(page).toHaveURL(url => `${url.pathname}${url.search}` === scenario.destination)
    if (scenario.roles.includes('CANDIDATE')) {
      await expect(page.getByRole('dialog').getByRole('heading', { name: '通知测试岗位' })).toBeVisible()
    }
  })
}
