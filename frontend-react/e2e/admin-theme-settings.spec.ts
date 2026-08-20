import { expect, test } from '@playwright/test'

test.use({ reducedMotion: 'no-preference', video: 'off' })

test('admin theme setting disables the global mouse follower immediately and persists', async ({ page }) => {
  let mouseFollowerEnabled = true
  const updates: boolean[] = []

  await page.addInitScript(() => {
    localStorage.setItem('access_token', 'theme-settings-access')
    localStorage.setItem('refresh_token', 'theme-settings-refresh')
  })
  await page.route('**/api/**', route => route.fulfill({ status: 404, json: { message: '未提供测试数据' } }))
  await page.route('**/api/v1/auth/me', route => route.fulfill({ json: { data: { id: 'theme-admin', username: 'theme_admin', realName: '主题管理员', roles: ['ADMIN'] } } }))
  await page.route('**/api/v1/notifications**', route => route.fulfill({ json: { data: { records: [], total: 0, pageNo: 1, pageSize: 50 } } }))
  await page.route('**/api/v1/platform/ui-settings', route => route.fulfill({ json: { data: { mouseFollowerEnabled } } }))
  await page.route('**/api/v1/admin/platform/ui-settings', route => {
    const body = route.request().postDataJSON() as { mouseFollowerEnabled: boolean }
    mouseFollowerEnabled = body.mouseFollowerEnabled
    updates.push(mouseFollowerEnabled)
    return route.fulfill({ json: { data: { mouseFollowerEnabled } } })
  })

  await page.goto('/admin/theme-settings')

  await expect(page.getByRole('heading', { name: '主题设置' })).toBeVisible()
  await expect(page.getByRole('link', { name: /主题设置/ })).toBeVisible()
  const toggle = page.getByRole('switch', { name: '鼠标跟随动画已开启' })
  await expect(toggle).toBeChecked()
  await expect(page.locator('.global-mouse-halo')).toHaveCount(1)
  await expect(page.locator('.global-mouse-dot')).toHaveCount(1)

  await toggle.click()

  await expect.poll(() => updates).toEqual([false])
  await expect(page.getByRole('switch', { name: '鼠标跟随动画已关闭' })).not.toBeChecked()
  await expect(page.getByText('设置已同步到平台。')).toBeVisible()
  await expect(page.locator('.global-mouse-halo')).toHaveCount(0)
  await expect(page.locator('.global-mouse-dot')).toHaveCount(0)

  await page.reload()
  await expect(page.getByRole('switch', { name: '鼠标跟随动画已关闭' })).not.toBeChecked()
  await expect(page.locator('.global-mouse-halo')).toHaveCount(0)
  await expect(page.locator('.global-mouse-dot')).toHaveCount(0)
})
