import { expect, test } from '@playwright/test'

test.use({ video: 'off' })

test('features page presents the recruitment evidence chain and preserves entry destinations', async ({ page }) => {
  await page.goto('/features')

  await expect(page.getByRole('heading', { name: /见人，见岗，\s*见依据。/ })).toBeVisible()
  await expect(page.getByRole('heading', { name: '招聘结论，都有来处。' })).toBeVisible()

  const evidenceItems = page.locator('.features-evidence-item')
  await expect(evidenceItems).toHaveCount(3)
  await expect(evidenceItems.nth(0)).toContainText('系统记录')
  await expect(evidenceItems.nth(1)).toContainText('模型辅助')
  await expect(evidenceItems.nth(2)).toContainText('企业决定')

  await page.getByRole('button', { name: '进入平台' }).first().click()
  await expect(page).toHaveURL(/\/login$/)

  await page.goto('/features')
  await page.getByRole('button', { name: '查看招聘岗位' }).click()
  await expect(page).toHaveURL(/\/login\?next=%2Fjobs$/)
})

test('features page keeps its fixed navigation and mobile layout within the viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/features')

  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.evaluate(() => window.scrollTo(0, 700))
  await expect.poll(() => page.locator('.features-nav').evaluate(element => element.getBoundingClientRect().top)).toBe(0)
  await expect(page.locator('#top.features-page')).toHaveCount(1)
})
