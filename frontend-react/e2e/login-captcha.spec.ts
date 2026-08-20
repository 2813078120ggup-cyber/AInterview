import { expect, test, type Locator, type Page } from '@playwright/test'

test.use({ video: 'off' })

const user = { id: 'login-e2e', username: 'login_e2e', realName: '登录测试用户', roles: ['CANDIDATE'] }
const transparentPng = `data:image/png;base64,${Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M/wHwAF/gL+3MxZ5wAAAABJRU5ErkJggg==', 'base64').toString('base64')}`
type Purpose = 'PASSWORD_LOGIN' | 'LOGIN_CODE_SEND' | 'REGISTER_CODE_SEND' | 'PASSWORD_RESET_CODE_SEND'

async function mockAuth(page: Page) {
  const challenges: Array<{ purpose: Purpose; challengeId: string; imageDataUrl: string }> = []
  const passwordLogins: Array<Record<string, string>> = []
  const loginCodeRequests: Array<Record<string, string>> = []
  const codeLogins: Array<Record<string, string>> = []
  const registerCodeRequests: Array<Record<string, string>> = []
  const registrations: Array<Record<string, string>> = []
  const companyRegistrations: Array<Record<string, string>> = []
  const passwordResetCodeRequests: Array<Record<string, string>> = []
  const passwordResetVerifyRequests: Array<Record<string, string>> = []
  const passwordResetCompleteRequests: Array<Record<string, string>> = []

  await page.route('**/api/**', route => route.fulfill({ json: { data: [] } }))
  await page.route('**/api/v1/auth/captcha/challenge', route => {
    const body = route.request().postDataJSON() as { purpose: Purpose }
    const challenge = { purpose: body.purpose, challengeId: `challenge-${body.purpose}`, imageDataUrl: transparentPng }
    challenges.push(challenge)
    return route.fulfill({ json: { data: { challengeId: challenge.challengeId, imageDataUrl: challenge.imageDataUrl, expiresInSeconds: 120 } } })
  })
  await page.route('**/api/v1/auth/login', route => {
    passwordLogins.push(route.request().postDataJSON() as Record<string, string>)
    return route.fulfill({ json: { data: { token: 'access-login-e2e', refreshToken: 'refresh-login-e2e', user } } })
  })
  await page.route('**/api/v1/auth/login/code/send', route => {
    loginCodeRequests.push(route.request().postDataJSON() as Record<string, string>)
    return route.fulfill({ json: { data: { accepted: true, cooldownSeconds: 60, expiresInSeconds: 300, message: '验证码已发送' } } })
  })
  await page.route('**/api/v1/auth/login/code', route => {
    codeLogins.push(route.request().postDataJSON() as Record<string, string>)
    return route.fulfill({ json: { data: { token: 'access-code-e2e', refreshToken: 'refresh-code-e2e', user } } })
  })
  await page.route('**/api/v1/auth/register/code', route => {
    registerCodeRequests.push(route.request().postDataJSON() as Record<string, string>)
    return route.fulfill({ json: { data: { accepted: true, cooldownSeconds: 60, expiresInSeconds: 300, message: '验证码已发送' } } })
  })
  await page.route('**/api/v1/auth/register', route => {
    registrations.push(route.request().postDataJSON() as Record<string, string>)
    return route.fulfill({ json: { data: null } })
  })
  await page.route('**/api/v1/auth/company/register', route => {
    companyRegistrations.push(route.request().postDataJSON() as Record<string, string>)
    return route.fulfill({ json: { data: { companyId: 100, companyCode: 'ENT-E2E', admin: { realName: 'HR 测试', roles: ['COMPANY_ADMIN'], companyId: 100 } } } })
  })
  await page.route('**/api/v1/auth/password/reset/code', route => {
    passwordResetCodeRequests.push(route.request().postDataJSON() as Record<string, string>)
    return route.fulfill({ json: { data: { accepted: true, cooldownSeconds: 60, expiresInSeconds: 300, message: '若该联系方式可用于找回账户，验证码将发送至该联系方式' } } })
  })
  await page.route('**/api/v1/auth/password/reset/verify', route => {
    passwordResetVerifyRequests.push(route.request().postDataJSON() as Record<string, string>)
    return route.fulfill({ json: { data: { resetToken: 'reset-ticket-e2e', expiresInSeconds: 600 } } })
  })
  await page.route('**/api/v1/auth/password/reset/complete', route => {
    passwordResetCompleteRequests.push(route.request().postDataJSON() as Record<string, string>)
    return route.fulfill({ json: { data: { sessionBehavior: '密码已重置，全部设备会话已失效，请重新登录' } } })
  })
  await page.route('**/api/v1/auth/me', route => route.fulfill({ json: { data: user } }))

  return { challenges, passwordLogins, loginCodeRequests, codeLogins, registerCodeRequests, registrations, companyRegistrations, passwordResetCodeRequests, passwordResetVerifyRequests, passwordResetCompleteRequests }
}

async function enterImageCaptcha(page: Page) {
  await page.getByPlaceholder('图形验证码').fill('ABCD')
}

async function expectInlineImageCaptcha(page: Page, reference: Locator) {
  const [referenceBox, captchaBox] = await Promise.all([
    reference.boundingBox(),
    page.getByPlaceholder('图形验证码').locator('..').boundingBox(),
  ])
  expect(referenceBox).not.toBeNull()
  expect(captchaBox).not.toBeNull()
  expect(Math.abs(referenceBox!.width - captchaBox!.width)).toBeLessThanOrEqual(1)
  expect(Math.abs(referenceBox!.height - captchaBox!.height)).toBeLessThanOrEqual(1)
}

async function expectAuthBrandBackground(page: Page) {
  const background = page.locator('[data-testid="auth-brand-background"], .auth-brand-background')
  await expect(background.first()).toBeAttached()
  return background.first()
}

async function expectOpaqueAuthPanel(page: Page) {
  const panel = page.locator('.auth-flow-panel')
  await expect(panel).toBeVisible()
  const surface = await panel.evaluate(element => {
    const style = getComputedStyle(element)
    return { backgroundColor: style.backgroundColor, borderWidth: style.borderTopWidth, borderStyle: style.borderTopStyle }
  })
  expect(surface.backgroundColor).not.toBe('rgba(0, 0, 0, 0)')
  expect(Number.parseFloat(surface.borderWidth)).toBeGreaterThan(0)
  expect(surface.borderStyle).toBe('solid')
}

async function expectFixedAuthHeader(page: Page) {
  const header = page.locator('.auth-public-nav')
  await expect(header).toBeVisible()
  const position = await header.evaluate(element => {
    const style = getComputedStyle(element)
    const rect = element.getBoundingClientRect()
    return { position: style.position, top: Math.round(rect.top) }
  })
  expect(position).toEqual({ position: 'fixed', top: 0 })
}

test('default password login accepts username phone email and submits image captcha fields', async ({ page }) => {
  const mock = await mockAuth(page)
  await page.goto('/login')

  await expect(page.getByLabel('用户名 / 手机号 / 邮箱')).toBeVisible()
  await expectInlineImageCaptcha(page, page.getByLabel('用户名 / 手机号 / 邮箱'))
  await expectAuthBrandBackground(page)
  await expect(page.getByRole('link', { name: 'AInterview 首页' })).toBeVisible()
  await expect(page.getByRole('link', { name: '返回首页' })).toBeVisible()
  await expectFixedAuthHeader(page)
  await expect(page.getByRole('heading', { name: 'AInterview', exact: true })).toBeVisible()
  await expect(page.getByText('每一面，都算数。')).toBeVisible()
  await expect(page.getByPlaceholder('密码')).toBeVisible()
  await expect(page.getByRole('tab', { name: '验证码登录', exact: true })).toBeVisible()
  const loginButton = page.getByRole('button', { name: '登录', exact: true })
  await expect(loginButton).toBeDisabled()
  await enterImageCaptcha(page)
  await expect(loginButton).toBeDisabled()
  await page.getByLabel('用户名 / 手机号 / 邮箱').fill('person@example.com')
  await expect(loginButton).toBeDisabled()
  await page.getByPlaceholder('密码').fill('Password123')
  await expect(loginButton).toBeEnabled()
  await loginButton.click()

  await expect.poll(() => mock.passwordLogins.length).toBe(1)
  expect(mock.passwordLogins[0]).toEqual({ username: 'person@example.com', password: 'Password123', captchaChallengeId: 'challenge-PASSWORD_LOGIN', captchaCode: 'ABCD' })
  expect(mock.challenges).toEqual(expect.arrayContaining([{ purpose: 'PASSWORD_LOGIN', challengeId: 'challenge-PASSWORD_LOGIN', imageDataUrl: transparentPng }]))
})

test('code login auto-detects email and does not submit channel on send or final login', async ({ page }) => {
  const mock = await mockAuth(page)
  await page.goto('/login')
  await expectAuthBrandBackground(page)
  await page.getByRole('tab', { name: '验证码登录', exact: true }).click()
  const sendButton = page.getByRole('button', { name: '发送验证码', exact: true })
  const loginButton = page.getByRole('button', { name: '登录', exact: true })
  await expect(sendButton).toBeDisabled()
  await expect(loginButton).toBeDisabled()
  await page.getByLabel('手机号 / 邮箱').fill('person@example.com')
  await expect(sendButton).toBeDisabled()
  await enterImageCaptcha(page)
  await expect(sendButton).toBeEnabled()
  await sendButton.click()
  await expect(loginButton).toBeDisabled()
  await page.getByLabel('短信 / 邮箱验证码').fill('123456')
  await expect(loginButton).toBeEnabled()
  await loginButton.click()

  await expect.poll(() => mock.loginCodeRequests.length).toBe(1)
  expect(mock.loginCodeRequests[0]).toEqual({ target: 'person@example.com', captchaChallengeId: 'challenge-LOGIN_CODE_SEND', captchaCode: 'ABCD' })
  await expect.poll(() => mock.codeLogins.length).toBe(1)
  expect(mock.codeLogins[0]).toEqual({ target: 'person@example.com', verificationCode: '123456' })
  expect(mock.codeLogins[0].channel).toBeUndefined()
  await expect(page).toHaveURL(/\/workspace$/)
})

test('candidate registration keeps email optional and uses image captcha for registration code', async ({ page }) => {
  const mock = await mockAuth(page)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/login')
  await page.getByRole('button', { name: '立即注册', exact: true }).click()
  await expect(page).toHaveURL(/\/register$/)
  await expectAuthBrandBackground(page)
  await expectOpaqueAuthPanel(page)
  await expectFixedAuthHeader(page)
  await expect(page.getByRole('heading', { name: '个人用户注册', exact: true })).toBeVisible()
  await expectInlineImageCaptcha(page, page.getByLabel('手机号'))
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight))
  await expectFixedAuthHeader(page)
  await page.evaluate(() => window.scrollTo(0, 0))
  const sendButton = page.getByRole('button', { name: '发送验证码', exact: true })
  const continueButton = page.getByRole('button', { name: '继续填写' })
  await expect(sendButton).toBeDisabled()
  await expect(continueButton).toBeDisabled()
  await page.getByLabel('手机号').fill('13800138000')
  await expect(sendButton).toBeDisabled()
  await enterImageCaptcha(page)
  await expect(sendButton).toBeEnabled()
  await sendButton.click()
  await page.getByLabel('短信验证码').fill('654321')
  await expect(continueButton).toBeEnabled()
  await continueButton.click()
  const createButton = page.getByRole('button', { name: '创建账户' })
  await expect(createButton).toBeDisabled()
  await page.getByLabel('姓名').fill('注册测试')
  await page.getByLabel('用户名').fill('register_e2e')
  await expect(createButton).toBeDisabled()
  await page.getByLabel('密码', { exact: true }).fill('Password123')
  await expect(createButton).toBeEnabled()
  await createButton.click()

  await expect.poll(() => mock.registrations.length).toBe(1)
  expect(mock.registerCodeRequests[0]).toEqual({ phone: '13800138000', captchaChallengeId: 'challenge-REGISTER_CODE_SEND', captchaCode: 'ABCD' })
  expect(mock.registerCodeRequests[0].email).toBeUndefined()
  expect(mock.registrations[0]).toEqual({ username: 'register_e2e', password: 'Password123', realName: '注册测试', phone: '13800138000', verificationCode: '654321' })
  expect(mock.registrations[0].email).toBeUndefined()
  await expect(page.getByRole('heading', { name: '账户创建成功' })).toBeVisible()
})

test('enterprise registration collects tenant and HR administrator details', async ({ page }) => {
  const mock = await mockAuth(page)
  await page.goto('/register?type=company')
  await expect(page.getByRole('heading', { name: '企业 HR 注册', exact: true })).toBeVisible()
  await page.getByLabel('手机号').fill('13800138001')
  await enterImageCaptcha(page)
  await page.getByRole('button', { name: '发送验证码', exact: true }).click()
  await page.getByLabel('短信验证码').fill('654321')
  await page.getByRole('button', { name: '继续填写' }).click()

  await page.getByLabel('企业全称').fill('示例科技有限公司')
  await page.getByLabel('所属行业').fill('人工智能')
  await page.getByLabel('企业规模').fill('100-499 人')
  await page.getByLabel('所在城市').fill('北京')
  await page.getByLabel('HR 联系人姓名').fill('HR 测试')
  await page.getByLabel('登录用户名').fill('hr_example')
  await page.getByRole('textbox', { name: '登录密码' }).fill('Password123')
  await page.getByRole('button', { name: '创建企业账号' }).click()

  await expect.poll(() => mock.companyRegistrations.length).toBe(1)
  expect(mock.companyRegistrations[0]).toMatchObject({
    phone: '13800138001', verificationCode: '654321', companyName: '示例科技有限公司',
    industry: '人工智能', companySize: '100-499 人', city: '北京', username: 'hr_example',
  })
  await expect(page.getByRole('heading', { name: '企业 HR 账号创建成功' })).toBeVisible()
  await expect(page.getByText('企业识别码：ENT-E2E')).toBeVisible()
})

test('forgot password verifies contact ownership before allowing a new password', async ({ page }) => {
  const mock = await mockAuth(page)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/login')
  await page.getByRole('button', { name: '忘记密码', exact: true }).click()

  await expect(page).toHaveURL(/\/forgot-password$/)
  await expectAuthBrandBackground(page)
  await expectOpaqueAuthPanel(page)
  await expectFixedAuthHeader(page)
  await expect(page.getByRole('heading', { name: '找回密码', exact: true })).toBeVisible()
  await expectInlineImageCaptcha(page, page.getByLabel('手机号 / 邮箱'))
  const sendButton = page.getByRole('button', { name: '发送验证码', exact: true })
  const verifyButton = page.getByRole('button', { name: '验证并继续' })
  await expect(sendButton).toBeDisabled()
  await expect(verifyButton).toBeDisabled()
  await page.getByLabel('手机号 / 邮箱').fill('person@example.com')
  await expect(sendButton).toBeDisabled()
  await enterImageCaptcha(page)
  await expect(sendButton).toBeEnabled()
  await sendButton.click()
  await page.getByLabel('短信 / 邮箱验证码').fill('123456')
  await expect(verifyButton).toBeEnabled()
  await verifyButton.click()

  await expect(page.getByRole('heading', { name: '设置新密码' })).toBeVisible()
  const saveButton = page.getByRole('button', { name: '保存新密码' })
  await expect(saveButton).toBeDisabled()
  await page.getByLabel('新密码', { exact: true }).fill('Next12345')
  await expect(saveButton).toBeDisabled()
  await page.getByLabel('确认新密码', { exact: true }).fill('Next12345')
  await expect(saveButton).toBeEnabled()
  await saveButton.click()

  expect(mock.passwordResetCodeRequests[0]).toEqual({ channel: 'email', target: 'person@example.com', captchaChallengeId: 'challenge-PASSWORD_RESET_CODE_SEND', captchaCode: 'ABCD' })
  expect(mock.passwordResetVerifyRequests[0]).toEqual({ channel: 'email', target: 'person@example.com', verificationCode: '123456' })
  expect(mock.passwordResetCompleteRequests[0]).toEqual({ resetToken: 'reset-ticket-e2e', newPassword: 'Next12345' })
  await expect(page.getByRole('heading', { name: '密码重置完成' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
})

test('login motion becomes static when reduced motion is requested', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.setViewportSize({ width: 390, height: 844 })
  await mockAuth(page)
  await page.goto('/login')

  await expect(page.getByRole('heading', { name: 'AInterview', exact: true })).toBeVisible()
  await expectAuthBrandBackground(page)

  const motion = await page.evaluate(() => {
    const card = document.querySelector<HTMLElement>('.auth-login-card')
    const shell = document.querySelector<HTMLElement>('.auth-login-shell')
    const indicator = document.querySelector<HTMLElement>('.auth-tab-indicator')
    const backgrounds = [...document.querySelectorAll<HTMLElement>('[data-testid="auth-brand-background"], .auth-brand-background')]
      .flatMap(element => [element, ...Array.from(element.querySelectorAll<HTMLElement>('*'))])
    if (!card || !shell || !indicator || !backgrounds.length) throw new Error('登录动效元素未完成渲染')

    return {
      cardAnimation: getComputedStyle(card).animationName,
      backdropAnimation: getComputedStyle(shell, '::before').animationName,
      backgroundAnimations: backgrounds.map(element => ({ name: getComputedStyle(element).animationName, iteration: getComputedStyle(element).animationIterationCount })),
      indicatorTransition: getComputedStyle(indicator).transitionDuration,
      noOverflow: document.documentElement.scrollWidth <= window.innerWidth,
    }
  })
  expect(motion.cardAnimation).toBe('none')
  expect(motion.backdropAnimation).toBe('none')
  expect(motion.backgroundAnimations.every(animation => animation.name === 'none' || animation.iteration !== 'infinite')).toBe(true)
  expect(motion.indicatorTransition).toBe('0s')
  expect(motion.noOverflow).toBe(true)

  await page.getByRole('tab', { name: '验证码登录' }).click()
  await expect(page.getByText('请输入已注册的手机号或邮箱，系统将自动选择验证渠道。')).toBeVisible()
})
