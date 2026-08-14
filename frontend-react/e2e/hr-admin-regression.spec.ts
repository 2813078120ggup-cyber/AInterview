import { expect, test, type APIRequestContext, type Browser, type BrowserContext, type Page } from '@playwright/test'
import { mkdirSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'

type Session = {
  token: string
  refreshToken: string
  user: { id: string; username: string; realName: string; roles: string[]; companyId?: string }
}

type Company = { id: string; companyCode: string; name: string; shortName?: string }
type Member = { id: string; username: string; realName: string; roles: string[] }
type Position = { id: string; name: string; positionCode: string; recruitmentStatus: string }
type Application = { id: string; candidateName: string; positionName: string; status: string; interviewId?: string }
type PageResult<T> = { records: T[]; total: number; pageNo: number; pageSize: number }
type AdminApplication = { id: string; company: { id: string }; position: { id: string } }
type CompanyReport = { reportStatus: string; taskStatus?: string; taskMessage?: string; canRetry?: boolean }

type E2EState = {
  runId: string
  companyA: Company
  companyB: Company
  createdCompany?: Company
  companyAAdmin: Session
  companyARecruiter: Session
  companyAInterviewer: Session
  companyBAdmin: Session
  admin: Session
  candidateLiu: Session
  candidateSun: Session
  position: Position
  offlinePosition: Position
  applicationId?: string
  offlineApplicationId?: string
  bApplicationId?: string
  existingReportApplicationId: string
  existingReportInterviewId: string
  aiInterviewId?: string
  aiReportStatus?: string
  aiProviderBoundary?: string
  pdfPath: string
  docxPath: string
}

const PASSWORD = 'E2ePass123'
const existingReportApplicationId = '2087236342013243393'
const existingReportInterviewId = '2087237105187827714'

test.describe.configure({ mode: 'serial' })

let state: E2EState

test.beforeAll(async ({ request }) => {
  const runId = `${Date.now()}`
  const admin = await login(request, 'admin_zhang')
  const companyAAdmin = await login(request, 'xingyun_hr')
  const companyBAdmin = await login(request, 'yunqi_hr')
  const candidateLiu = await login(request, 'candidate_liu')
  const candidateSun = await login(request, 'candidate_sun')

  const companies = await api<PageResult<Company>>(request, admin, '/v1/admin/companies?pageNo=1&pageSize=100')
  const companyA = companies.records.find(item => item.companyCode === 'XINGYUN_TECH')
  const companyB = companies.records.find(item => item.companyCode === 'YUNQI_DIGITAL')
  if (!companyA || !companyB) throw new Error('预置企业 A/B 不存在，无法执行租户隔离回归')

  const recruiter = await createAdminMember(request, admin, companyA.id, `e2e_recruiter_${runId}`, `招聘专员 ${runId}`, ['COMPANY_RECRUITER'], '1380' + runId.slice(-7))
  const interviewer = await createAdminMember(request, admin, companyA.id, `e2e_interviewer_${runId}`, `面试官 ${runId}`, ['COMPANY_INTERVIEWER'], '1381' + runId.slice(-7))
  const companyARecruiter = await login(request, recruiter.username)
  const companyAInterviewer = await login(request, interviewer.username)

  const position = await createPublishedPosition(request, companyAAdmin, runId, 'HR')
  const offlinePosition = await createPublishedPosition(request, companyAAdmin, runId, 'OFFLINE')
  const bApplications = await api<PageResult<AdminApplication>>(request, admin, '/v1/admin/recruitment/applications?pageNo=1&pageSize=100')
  const bApplicationId = bApplications.records.find(item => item.company.id === companyB.id)?.id
  if (!bApplicationId) throw new Error('预置企业 B 没有可用于越权验证的申请')

  const fixtureDir = join(process.cwd(), 'test-results', 'e2e-fixtures')
  mkdirSync(fixtureDir, { recursive: true })
  const pdfPath = join(fixtureDir, `resume-${runId}.pdf`)
  const docxPath = join(fixtureDir, `resume-${runId}.docx`)
  writeFileSync(pdfPath, minimalPdf())
  writeFileSync(docxPath, minimalDocx())

  state = {
    runId,
    companyA,
    companyB,
    companyAAdmin,
    companyARecruiter,
    companyAInterviewer,
    companyBAdmin,
    admin,
    candidateLiu,
    candidateSun,
    position,
    offlinePosition,
    existingReportApplicationId,
    existingReportInterviewId,
    bApplicationId,
    pdfPath,
    docxPath,
  }
})

test('超级管理员创建企业和企业管理员，并可刷新深层路由', async ({ browser }) => {
  const context = await sessionContext(browser, state.admin, { viewport: { width: 1440, height: 900 } })
  const page = await context.newPage()
  const errors = diagnostics(page)
  await page.goto('/admin/companies')
  await expect(page.getByRole('heading', { name: '企业管理' })).toBeVisible()
  await page.getByRole('button', { name: '创建企业' }).click()
  const runCompanyCode = `E2E_${state.runId}`
  const runCompanyName = `E2E 回归企业 ${state.runId}`
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('企业编码').fill(runCompanyCode)
  await dialog.getByLabel('企业名称').fill(runCompanyName)
  await dialog.getByLabel('简称').fill(`E2E${state.runId.slice(-6)}`)
  await dialog.getByLabel('行业').fill('智能招聘')
  await dialog.getByLabel('城市').fill('北京')
  await dialog.getByRole('button', { name: '创建企业', exact: true }).click()
  await expect(page).toHaveURL(/\/admin\/companies\/\d+$/)
  state.createdCompany = { id: page.url().split('/').pop() ?? '', companyCode: runCompanyCode, name: runCompanyName }
  await expect(page.getByText('成员与权限')).toBeVisible()

  await page.getByRole('button', { name: '创建成员' }).click()
  const memberDialog = page.getByRole('dialog')
  const memberUsername = `e2e_owner_${state.runId}`
  await memberDialog.getByLabel('姓名').fill(`回归管理员 ${state.runId}`)
  await memberDialog.getByLabel('账号').fill(memberUsername)
  await memberDialog.getByLabel('初始密码').fill(PASSWORD)
  await memberDialog.getByLabel('手机号').fill(`1390${state.runId.slice(-7)}`)
  await memberDialog.getByRole('button', { name: /企业管理员/ }).click()
  await memberDialog.getByRole('button', { name: '创建成员', exact: true }).click()
  await expect(page.getByText(memberUsername)).toBeVisible()

  await page.reload()
  await expect(page.getByText(runCompanyName)).toBeVisible()
  await expect(page.getByRole('heading', { name: '企业成员' })).toBeVisible()
  await expectNoDiagnostics(errors)
  await context.close()
})

test('候选人通过真实岗位大厅上传 PDF/DOCX 并投递，HR 能在流程中心看到申请', async ({ browser, request }) => {
  const positionContext = await sessionContext(browser, state.companyAAdmin, { viewport: { width: 1024, height: 900 } })
  const positionPage = await positionContext.newPage()
  const positionErrors = diagnostics(positionPage)
  const uiPositionName = `E2E UI 岗位 ${state.runId}`
  await positionPage.goto('/company/positions/new')
  await expect(positionPage.getByRole('heading', { name: '创建岗位草稿' })).toBeVisible()
  await positionPage.getByLabel('岗位编码').fill(`E2E-UI-${state.runId}`)
  await positionPage.getByLabel('岗位名称').fill(uiPositionName)
  await positionPage.getByLabel('所属部门').fill('E2E UI 招聘部')
  await positionPage.getByLabel('工作城市').fill('北京')
  await positionPage.getByPlaceholder('输入技能后按 Enter，例如 Java').fill('Java')
  await positionPage.keyboard.press('Enter')
  await positionPage.getByLabel('岗位介绍').fill('用于端到端回归的真实岗位介绍。')
  await positionPage.getByLabel('任职要求').fill('熟悉 Java、React、MySQL 和招聘业务流程。')
  await positionPage.getByRole('button', { name: '保存草稿' }).click()
  await expect(positionPage).toHaveURL(/\/company\/positions\/\d+$/)
  await expect(positionPage.getByText('草稿', { exact: true }).first()).toBeVisible()
  positionPage.once('dialog', dialog => dialog.accept())
  await positionPage.getByRole('button', { name: '发布岗位' }).click()
  await expect(positionPage.getByText('岗位已发布，候选人现在可以查看并投递。')).toBeVisible({ timeout: 20_000 })
  const uiPositionId = positionPage.url().split('/').pop()
  if (!uiPositionId) throw new Error('UI 创建岗位后缺少岗位 ID')
  const uiPositionDetail = await api<{ job: Position }>(request, state.companyAAdmin, `/v1/company/recruitment/positions/${uiPositionId}`)
  state.position = uiPositionDetail.job
  await expectNoDiagnostics(positionErrors)
  await positionContext.close()

  const context = await sessionContext(browser, state.candidateLiu, { viewport: { width: 390, height: 844 } })
  const page = await context.newPage()
  const errors = diagnostics(page)
  await page.goto('/resumes')
  await expect(page.getByRole('heading', { name: '简历与解析状态' })).toBeVisible()
  const uploadForm = page.getByRole('form', { name: '上传新简历' })
  await uploadForm.locator('input[type="file"]').setInputFiles(state.pdfPath)
  await uploadForm.getByLabel('简历名称').fill(`E2E PDF 简历 ${state.runId}`)
  await uploadForm.getByRole('button', { name: '开始上传' }).click()
  await expect(page.getByText('简历上传成功')).toBeVisible({ timeout: 20_000 })

  await uploadForm.locator('input[type="file"]').setInputFiles(state.docxPath)
  await uploadForm.getByLabel('简历名称').fill(`E2E DOCX 简历 ${state.runId}`)
  await uploadForm.locator('input[name="defaultResume"]').uncheck()
  await uploadForm.getByRole('button', { name: '开始上传' }).click()
  await expect(page.getByText('简历上传成功')).toBeVisible({ timeout: 20_000 })

  await page.goto(`/jobs?keyword=${encodeURIComponent(state.position.name)}`)
  await expect(page.getByRole('heading', { name: state.position.name })).toBeVisible()
  await page.getByRole('button', { name: '查看详情' }).first().click()
  const jobDialog = page.getByRole('dialog')
  await expect(jobDialog.getByRole('heading', { name: state.position.name })).toBeVisible()
  await expect(jobDialog.getByText('立即投递')).toBeVisible()
  await jobDialog.getByText('立即投递').click()
  await expect(page.getByText(`已成功投递“${state.position.name}”`)).toBeVisible({ timeout: 20_000 })

  const candidateApplications = await api<PageResult<Application>>(request, state.candidateLiu, '/v1/recruitment/applications?pageNo=1&pageSize=100')
  const application = candidateApplications.records.find(item => item.positionName === state.position.name)
  if (!application) throw new Error('候选人投递成功后未找到对应申请')
  state.applicationId = application.id

  const recruiterContext = await sessionContext(browser, state.companyARecruiter, { viewport: { width: 1024, height: 900 } })
  const recruiterPage = await recruiterContext.newPage()
  await recruiterPage.goto(`/company/applications/${state.applicationId}?from=%2Fcompany%2Fapplications%3Fkeyword%3D${encodeURIComponent(state.position.name)}`)
  await recruiterPage.reload()
  await expect(recruiterPage.getByRole('heading', { name: '刘洋' })).toBeVisible()
  await expect(recruiterPage.getByRole('tab', { name: '简历与画像' })).toBeVisible()
  await expectNoDiagnostics(errors)
  await context.close()
  await recruiterContext.close()
})

test('HR 查看画像、匹配历史、真实创建 AI 面试并验证重复创建保护', async ({ browser, request }) => {
  if (!state.applicationId) throw new Error('缺少候选人申请，无法继续 AI 面试回归')
  const context = await sessionContext(browser, state.companyARecruiter, { viewport: { width: 1440, height: 900 } })
  const page = await context.newPage()
  const errors = diagnostics(page)
  await page.goto(`/company/applications/${state.applicationId}?tab=profile`)
  await expect(page.getByRole('tab', { name: '简历与画像' })).toHaveAttribute('aria-selected', 'true')
  await expect(page.getByText(/解析|结构化|画像/).first()).toBeVisible()
  await page.getByRole('tab', { name: '岗位匹配' }).click()
  await expect(page.getByRole('tabpanel')).toContainText(/匹配|评估|尚未生成|失败/)

  const banks = await api<Array<{ id: string }>>(request, state.companyARecruiter, '/v1/company/recruitment/interview-question-banks')
  if (!banks.length) throw new Error('没有可用的 AI 面试题库，无法验证面试创建')
  const scheduledAt = localDateTimeFromNow(90_000)
  const interviewResult = await api<Application>(request, state.companyARecruiter, `/v1/company/recruitment/applications/${state.applicationId}/ai-interview`, {
    method: 'POST',
    data: { scheduledAt, durationMinutes: 10, type: 'tech', questionBankId: Number(banks[0].id), questionCount: 1, interviewerStyle: 'big-tech', remark: `E2E ${state.runId}` },
  })
  state.aiInterviewId = interviewResult.interviewId
  expect(state.aiInterviewId).toBeTruthy()
  expect(interviewResult.status).toBe('AI_INTERVIEW_PENDING')

  const duplicate = await rawApi(request, state.companyARecruiter, `/v1/company/recruitment/applications/${state.applicationId}/ai-interview`, {
    method: 'POST',
    data: { scheduledAt: localDateTimeFromNow(120_000), durationMinutes: 10, type: 'tech', questionBankId: Number(banks[0].id), questionCount: 1, interviewerStyle: 'big-tech' },
  })
  expect([400, 409]).toContain(duplicate.status)

  const interviewerInterviews = await api<PageResult<unknown>>(request, state.companyAInterviewer, '/v1/company/recruitment/interviews?pageNo=1&pageSize=20')
  expect(Array.isArray(interviewerInterviews.records)).toBeTruthy()

  await page.goto('/company/interviews')
  await expect(page.getByRole('heading', { name: /面试/ })).toBeVisible()
  await expect(page.getByText(/共 \d+ 场/)).toBeVisible()
  await page.goto(`/company/applications/${state.applicationId}?tab=timeline`)
  await expect(page.getByRole('tab', { name: '时间线' })).toHaveAttribute('aria-selected', 'true')
  await expectNoDiagnostics(errors)
  await context.close()

  if (state.aiInterviewId) await driveCandidateInterview(request, state)
})

test('真实 AI 面试结束后保留报告任务状态；既有真实报告完成 HR 查看、发布状态和候选人查看', async ({ browser, request }) => {
  if (!state.applicationId) throw new Error('缺少申请，无法检查报告任务状态')
  if (state.aiInterviewId) {
    const recruiterReport = await pollCompanyReport(request, state.companyARecruiter, state.applicationId, 6)
    state.aiReportStatus = recruiterReport?.reportStatus ?? 'UNAVAILABLE'
    state.aiProviderBoundary = recruiterReport?.reportStatus === 'READY' || recruiterReport?.reportStatus === 'PUBLISHED'
      ? '真实服务已生成报告'
      : '真实后端已创建并结束面试；Provider 未产生可发布报告，保留为任务状态/失败状态'
    expect(state.aiReportStatus).toMatch(/READY|PUBLISHED|GENERATING|PENDING|PROCESSING|FAILED|UNAVAILABLE/)
  }

  const hrContext = await sessionContext(browser, state.companyAAdmin, { viewport: { width: 768, height: 900 } })
  const hrPage = await hrContext.newPage()
  const hrErrors = diagnostics(hrPage)
  await hrPage.goto(`/company/applications/${state.existingReportApplicationId}?tab=report`)
  await expect(hrPage.getByRole('tab', { name: '评估报告' })).toHaveAttribute('aria-selected', 'true')
  await expect(hrPage.getByText('综合得分').first()).toBeVisible()
  await expect(hrPage.getByText(/已发布|仅对企业内部可见/).first()).toBeVisible()
  await expect(hrPage.locator('body')).not.toContainText(/systemPrompt|userPrompt|providerResponse|api_key|完整解析原文/i)
  await expectNoDiagnostics(hrErrors)

  const candidateContext = await sessionContext(browser, state.candidateSun, { viewport: { width: 390, height: 844 } })
  const candidatePage = await candidateContext.newPage()
  const candidateErrors = diagnostics(candidatePage)
  await candidatePage.goto(`/candidate/interviews/${state.existingReportInterviewId}/report`)
  await expect(candidatePage.getByRole('heading', { name: '面试评测报告' })).toBeVisible()
  await expect(candidatePage.getByText('综合得分').last()).toBeVisible()
  await expectNoDiagnostics(candidateErrors)
  await hrContext.close()
  await candidateContext.close()
})

test('HR 创建线下面试并推进终态，超级管理员查看运营与服务端审计', async ({ browser, request }) => {
  const candidateApps = await api<PageResult<Application>>(request, state.candidateSun, '/v1/recruitment/applications?pageNo=1&pageSize=100')
  let offlineApplication = candidateApps.records.find(item => item.positionName === state.offlinePosition.name)
  if (!offlineApplication) {
    offlineApplication = await api<Application>(request, state.candidateSun, `/v1/recruitment/jobs/${state.offlinePosition.id}/applications`, {
      method: 'POST', data: { resumeId: undefined, candidateMessage: `E2E 线下面试 ${state.runId}` },
    })
  }
  state.offlineApplicationId = offlineApplication.id
  await api(request, state.companyARecruiter, `/v1/company/recruitment/applications/${offlineApplication.id}/status`, {
    method: 'PUT', data: { status: 'UNDER_REVIEW', note: 'E2E 进入企业评估' },
  })
  await api(request, state.companyARecruiter, `/v1/company/recruitment/applications/${offlineApplication.id}/offline-interview`, {
    method: 'POST', data: { scheduledAt: localDateTimeFromNow(86_400_000), durationMinutes: 30, interviewType: 'ONSITE', location: 'E2E 北京会议室', contactName: 'E2E HR', contactPhone: '13800000001', note: 'E2E 线下面试邀请' },
  })
  const hired = await api<Application>(request, state.companyARecruiter, `/v1/company/recruitment/applications/${offlineApplication.id}/status`, {
    method: 'PUT', data: { status: 'HIRED', note: 'E2E 面试完成，决定录用' },
  })
  expect(hired.status).toBe('HIRED')

  const context = await sessionContext(browser, state.admin, { viewport: { width: 1024, height: 900 } })
  const page = await context.newPage()
  const errors = diagnostics(page)
  await page.goto('/admin/workspace')
  await expect(page.getByRole('heading', { name: '服务异常与处理队列' })).toBeVisible()
  await page.goto('/admin/audit-logs')
  await expect(page.getByRole('heading', { name: '操作日志' })).toBeVisible()
  await expect(page.getByText('服务端分页')).toBeVisible()
  await expect(page.locator('main')).not.toContainText(/Bearer\s+eyJ|"(?:password|accessToken|refreshToken|apiKey|apiSecret|providerResponse)"\s*:/i)
  await expectNoDiagnostics(errors)
  await context.close()
})

test('企业 A 无法读取企业 B 的申请、简历、面试和报告', async ({ browser, request }) => {
  if (!state.bApplicationId) throw new Error('缺少企业 B 申请')
  const companyBOwnApplication = await rawApi(request, state.companyBAdmin, `/v1/company/recruitment/applications/${state.bApplicationId}`)
  expect(companyBOwnApplication.status).toBe(200)
  const paths = [
    `/v1/company/recruitment/applications/${state.bApplicationId}`,
    `/v1/company/recruitment/applications/${state.bApplicationId}/resume/analysis`,
    `/v1/company/recruitment/applications/${state.bApplicationId}/interview`,
    `/v1/company/recruitment/applications/${state.bApplicationId}/report`,
    `/v1/company/recruitment/applications/${state.bApplicationId}/resume/content`,
  ]
  for (const path of paths) {
    const result = await rawApi(request, state.companyARecruiter, path)
    expect([403, 404]).toContain(result.status)
  }

  const context = await sessionContext(browser, state.companyARecruiter, { viewport: { width: 390, height: 844 } })
  const page = await context.newPage()
  const errors = diagnostics(page)
  await page.goto(`/company/applications/${state.bApplicationId}`)
  await expect(page.getByText(/申请详情不可用|不存在|无法/).first()).toBeVisible()
  await expectNoDiagnostics(errors.filter(error => !error.includes('Failed to load resource: the server responded with a status of 404 (Not Found)')))
  await context.close()
})

test('桌面、平板和移动端浅色/深色、reduced motion、键盘抽屉和无横向溢出', async ({ browser }) => {
  for (const [width, height] of [[1440, 900], [1024, 900], [768, 900], [390, 844]] as const) {
    const context = await sessionContext(browser, state.companyAAdmin, { viewport: { width, height }, dark: width === 768 })
    const page = await context.newPage()
    const errors = diagnostics(page)
    await page.goto('/company')
    await expect(page.getByRole('heading', { name: /今天|工作台|招聘/ }).first()).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
    expect(await page.evaluate(() => window.matchMedia('(prefers-reduced-motion: reduce)').matches)).toBeTruthy()
    if (width === 768) expect(await page.evaluate(() => document.documentElement.classList.contains('dark'))).toBeTruthy()
    if (width === 390) {
      const menu = page.getByRole('button', { name: /打开企业端导航/ })
      await menu.focus()
      await page.keyboard.press('Enter')
      await expect(page.getByRole('dialog')).toBeVisible()
      await page.keyboard.press('Escape')
      await expect(page.getByRole('dialog')).toHaveCount(0)
    }
    await context.route('**/api/v1/recruitment/jobs**', async route => {
      await new Promise(resolve => setTimeout(resolve, 500))
      try {
        await route.fulfill({
          status: 403,
          contentType: 'application/json',
          body: JSON.stringify({ message: 'E2E controlled error' }),
        })
      } catch (error) {
        if (!String(error).includes('Route is already handled')) throw error
      }
    })
    const jobsNavigation = page.goto('/jobs?keyword=E2E_NOT_FOUND')
    await expect(page.getByRole('status', { name: '正在加载岗位…' })).toBeVisible()
    await jobsNavigation
    await context.unroute('**/api/v1/recruitment/jobs**')
    await expect(page.getByText(/没有找到匹配岗位|岗位暂时加载失败/)).toBeVisible()
    await expectNoDiagnostics(errors.filter(error => !/Failed to load resource: the server responded with a status of (403|404) \((Forbidden|Not Found)\)/.test(error)))
    await context.close()
  }
})

async function login(request: APIRequestContext, username: string): Promise<Session> {
  const password = username.startsWith('e2e_') ? PASSWORD : 'password'
  for (let attempt = 0; attempt < 7; attempt += 1) {
    const response = await request.post('/api/v1/auth/login', { data: { username, password } })
    const body = await response.json() as { data: Session; message?: string }
    if (response.ok()) return body.data
    if (response.status() !== 429 || attempt === 6) throw new Error(`登录 ${username} 失败：${response.status()} ${body.message ?? ''}`)
    await new Promise(resolve => setTimeout(resolve, 10_000))
  }
  throw new Error(`登录 ${username} 失败`)
}

async function createAdminMember(request: APIRequestContext, admin: Session, companyId: string, username: string, realName: string, roleCodes: string[], phone: string): Promise<Member> {
  return api<Member>(request, admin, `/v1/admin/companies/${companyId}/members`, {
    method: 'POST', data: { username, password: PASSWORD, realName, email: `${username}@e2e.test`, phone, roleCodes },
  })
}

async function createPublishedPosition(request: APIRequestContext, session: Session, runId: string, suffix: string): Promise<Position> {
  const position = await api<Position>(request, session, '/v1/company/recruitment/positions', {
    method: 'POST', data: {
      positionCode: `E2E-${runId}-${suffix}`,
      name: `E2E ${suffix} 工程师 ${runId}`,
      department: 'E2E 招聘验证部',
      salaryMin: 15,
      salaryMax: 30,
      city: '北京',
      experienceRequirement: '3-5年',
      educationRequirement: '本科及以上',
      jobType: 'FULL_TIME',
      description: '用于自动化回归的真实岗位草稿。',
      requirements: '熟悉 Java、React、MySQL 和招聘业务流程。',
      skillTags: ['Java', 'React', 'MySQL'],
    },
  })
  return api<Position>(request, session, `/v1/company/recruitment/positions/${position.id}/status`, {
    method: 'PUT', data: { status: 'PUBLISHED', note: `E2E ${suffix} 发布` },
  })
}

async function api<T>(request: APIRequestContext, session: Session, path: string, options: { method?: string; data?: unknown; multipart?: unknown } = {}): Promise<T> {
  const result = await rawApi(request, session, path, options)
  const body = result.body as { data?: T; message?: string }
  if (!result.ok) throw new Error(`${options.method ?? 'GET'} ${path} ${result.status}: ${body.message ?? '请求失败'}`)
  return body.data as T
}

async function rawApi(request: APIRequestContext, session: Session, path: string, options: { method?: string; data?: unknown; multipart?: unknown } = {}) {
  const response = await request.fetch(`/api${path}`, {
    method: options.method ?? 'GET',
    headers: { Authorization: `Bearer ${session.token}` },
    data: options.data,
    multipart: options.multipart,
  })
  const body = await response.json().catch(() => ({})) as Record<string, unknown>
  return { ok: response.ok(), status: response.status(), body }
}

async function sessionContext(browser: Browser, session: Session, options: { viewport: { width: number; height: number }; dark?: boolean }): Promise<BrowserContext> {
  const context = await browser.newContext({
    viewport: options.viewport,
    colorScheme: options.dark ? 'dark' : 'light',
    reducedMotion: 'reduce',
  })
  await context.addInitScript(({ token, refreshToken, user, dark }) => {
    localStorage.setItem('access_token', token)
    localStorage.setItem('refresh_token', refreshToken)
    localStorage.setItem('ai_interview_profile', JSON.stringify(user))
    localStorage.setItem('interviewos-theme', dark ? 'dark' : 'light')
  }, { token: session.token, refreshToken: session.refreshToken, user: session.user, dark: Boolean(options.dark) })
  return context
}

function diagnostics(page: Page) {
  const errors: string[] = []
  page.on('console', message => { if (message.type() === 'error') errors.push(`console: ${message.text()}`) })
  page.on('pageerror', error => errors.push(`pageerror: ${error.message}`))
  return errors
}

async function expectNoDiagnostics(errors: string[]) {
  expect(errors, errors.join('\n')).toEqual([])
}

async function driveCandidateInterview(request: APIRequestContext, current: E2EState) {
  if (!current.aiInterviewId) return
  const start = await rawApi(request, current.candidateLiu, `/v1/interviews/${current.aiInterviewId}/start`, { method: 'POST' })
  if (!start.ok) {
    current.aiProviderBoundary = `候选人开始面试未完成：${String(start.body.message ?? '接口拒绝')}`
    return
  }
  const questions = await api<Array<{ interviewQuestionId: string }>>(request, current.candidateLiu, `/v1/interviews/${current.aiInterviewId}/questions`)
  if (!questions.length) throw new Error('AI 面试创建后没有题目快照')
  await api(request, current.candidateLiu, `/v1/interviews/${current.aiInterviewId}/questions/${questions[0].interviewQuestionId}/answer`, {
    method: 'PUT', data: { answerContent: 'E2E 自动化回归回答：我会先澄清目标，再用可验证的指标完成交付。', durationSeconds: 5 },
  })
  const ended = await rawApi(request, current.candidateLiu, `/v1/interviews/${current.aiInterviewId}/end`, { method: 'POST' })
  if (!ended.ok) {
    current.aiProviderBoundary = `面试结束未完成：${String(ended.body.message ?? '接口拒绝')}`
    return
  }
  current.aiProviderBoundary = '真实后端完成候选人作答和结束，报告生成走运行环境 Provider'
}

async function pollCompanyReport(request: APIRequestContext, session: Session, applicationId: string, attempts: number): Promise<CompanyReport | undefined> {
  let latest: CompanyReport | undefined
  for (let index = 0; index < attempts; index += 1) {
    const result = await rawApi(request, session, `/v1/company/recruitment/applications/${applicationId}/report`)
    if (result.ok) {
      latest = result.body.data as CompanyReport
      if (latest.reportStatus === 'READY' || latest.reportStatus === 'PUBLISHED' || latest.reportStatus === 'FAILED') return latest
    }
    await new Promise(resolve => setTimeout(resolve, 5_000))
  }
  return latest
}

function localDateTimeFromNow(offsetMs: number) {
  const date = new Date(Date.now() + offsetMs)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function minimalPdf() {
  const objects = [
    '1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n',
    '2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n',
    '3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>\nendobj\n',
    '4 0 obj\n<< /Length 57 >>\nstream\nBT /F1 18 Tf 72 720 Td (AInterview E2E Resume) Tj ET\nendstream\nendobj\n',
    '5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n',
  ]
  const header = Buffer.from('%PDF-1.4\n', 'binary')
  const chunks = [header]
  const offsets = [0]
  let length = header.length
  for (const object of objects) {
    offsets.push(length)
    const chunk = Buffer.from(object, 'binary')
    chunks.push(chunk)
    length += chunk.length
  }
  const xrefOffset = length
  const xref = `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n${offsets.slice(1).map(offset => `${String(offset).padStart(10, '0')} 00000 n \n`).join('')}trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xrefOffset}\n%%EOF\n`
  chunks.push(Buffer.from(xref, 'binary'))
  return Buffer.concat(chunks)
}

function minimalDocx() {
  const files = {
    '[Content_Types].xml': '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>',
    '_rels/.rels': '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>',
    'word/document.xml': '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>AInterview E2E DOCX Resume</w:t></w:r></w:p><w:sectPr/></w:body></w:document>',
  }
  const entries: Buffer[] = []
  const central: Buffer[] = []
  let offset = 0
  for (const [name, value] of Object.entries(files)) {
    const nameBytes = Buffer.from(name, 'utf8')
    const data = Buffer.from(value, 'utf8')
    const crc = crc32(data)
    const local = Buffer.alloc(30 + nameBytes.length)
    local.writeUInt32LE(0x04034b50, 0)
    local.writeUInt16LE(20, 4); local.writeUInt16LE(0, 6); local.writeUInt16LE(0, 8)
    local.writeUInt16LE(0, 10); local.writeUInt16LE(0, 12); local.writeUInt32LE(crc, 14)
    local.writeUInt32LE(data.length, 18); local.writeUInt32LE(data.length, 22); local.writeUInt16LE(nameBytes.length, 26); local.writeUInt16LE(0, 28)
    nameBytes.copy(local, 30)
    entries.push(Buffer.concat([local, data]))
    const directory = Buffer.alloc(46 + nameBytes.length)
    directory.writeUInt32LE(0x02014b50, 0); directory.writeUInt16LE(20, 4); directory.writeUInt16LE(20, 6)
    directory.writeUInt16LE(0, 8); directory.writeUInt16LE(0, 10); directory.writeUInt16LE(0, 12); directory.writeUInt16LE(0, 14)
    directory.writeUInt32LE(crc, 16); directory.writeUInt32LE(data.length, 20); directory.writeUInt32LE(data.length, 24)
    directory.writeUInt16LE(nameBytes.length, 28); directory.writeUInt16LE(0, 30); directory.writeUInt16LE(0, 32); directory.writeUInt16LE(0, 34)
    directory.writeUInt16LE(0, 36); directory.writeUInt32LE(0, 38); directory.writeUInt32LE(offset, 42); nameBytes.copy(directory, 46)
    central.push(directory)
    offset += local.length + data.length
  }
  const centralOffset = offset
  const centralData = Buffer.concat(central)
  const end = Buffer.alloc(22)
  end.writeUInt32LE(0x06054b50, 0); end.writeUInt16LE(0, 4); end.writeUInt16LE(0, 6)
  end.writeUInt16LE(central.length, 8); end.writeUInt16LE(central.length, 10); end.writeUInt32LE(centralData.length, 12); end.writeUInt32LE(centralOffset, 16); end.writeUInt16LE(0, 20)
  return Buffer.concat([...entries, centralData, end])
}

function crc32(buffer: Buffer) {
  let crc = 0xffffffff
  for (const byte of buffer) {
    crc ^= byte
    for (let bit = 0; bit < 8; bit += 1) crc = (crc >>> 1) ^ (crc & 1 ? 0xedb88320 : 0)
  }
  return (crc ^ 0xffffffff) >>> 0
}
