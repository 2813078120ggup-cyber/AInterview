import { expect, test, type Page } from '@playwright/test'

test.use({ reducedMotion: 'reduce', video: 'off' })

type DataDictionaryFixture = {
  overviewCalls: string[]
  tableCalls: string[]
  detailCalls: string[]
  failTablesOnce?: boolean
  emptyTables?: boolean
}

const columns = [
  { ordinalPosition: 1, columnName: 'id', dataType: 'BIGINT', nativeType: 'BIGINT', length: null, scale: null, nullable: false, defaultValue: null, maskedDefaultValue: null, defaultValueMasked: false, autoIncrement: true, comment: '用户记录唯一标识', sensitive: false, maskingDefault: '' },
  { ordinalPosition: 2, columnName: 'username', dataType: 'VARCHAR', nativeType: 'VARCHAR(64)', length: 64, scale: null, nullable: false, defaultValue: null, maskedDefaultValue: null, defaultValueMasked: false, autoIncrement: false, comment: '登录账号', sensitive: false, maskingDefault: '' },
  { ordinalPosition: 3, columnName: 'email', dataType: 'VARCHAR', nativeType: 'VARCHAR(255)', length: 255, scale: null, nullable: true, defaultValue: null, maskedDefaultValue: null, defaultValueMasked: false, autoIncrement: false, comment: '联系邮箱，页面仅展示脱敏值', sensitive: true, maskingDefault: '脱敏展示' },
  { ordinalPosition: 4, columnName: 'password_hash', dataType: 'VARCHAR', nativeType: 'VARCHAR(255)', length: 255, scale: null, nullable: false, defaultValue: null, maskedDefaultValue: '[已脱敏]', defaultValueMasked: true, autoIncrement: false, comment: '仅存储不可逆摘要，不得读取原文', sensitive: true, maskingDefault: '禁止展示' },
]

const tables = [
  { tableName: 'user', name: 'user', tableComment: '平台用户、认证和账号状态', displayName: '用户账号', comment: '平台用户、认证和账号状态', tableType: 'TABLE', columnCount: 18, sensitiveColumnCount: 2, indexCount: 3, foreignKeyCount: 1, primaryKeyCount: 1, sensitivity: 'SENSITIVE', engine: 'InnoDB', primaryKeyColumns: ['id'] },
  { tableName: 'company', name: 'company', tableComment: '企业租户基础资料和状态', displayName: '企业租户', comment: '企业租户基础资料和状态', tableType: 'TABLE', columnCount: 15, sensitiveColumnCount: 1, indexCount: 2, foreignKeyCount: 0, primaryKeyCount: 1, sensitivity: 'INTERNAL', engine: 'InnoDB', primaryKeyColumns: ['id'] },
]

async function mockSession(page: Page) {
  await page.addInitScript(() => {
    localStorage.setItem('access_token', 'data-dictionary-admin-access')
    localStorage.setItem('refresh_token', 'data-dictionary-admin-refresh')
    localStorage.setItem('ai_interview_profile', JSON.stringify({
      id: 'dictionary-admin', username: 'dictionary_admin', realName: '数据管理员', roles: ['ADMIN'],
    }))
  })
}

async function mockAdminApis(page: Page, fixture: DataDictionaryFixture) {
  await page.route('**/api/**', route => route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ message: '未提供测试数据' }) }))
  await page.route('**/api/v1/auth/me', route => route.fulfill({ json: { data: { id: 'dictionary-admin', username: 'dictionary_admin', realName: '数据管理员', roles: ['ADMIN'] } } }))
  await page.route('**/api/v1/account/profile', route => route.fulfill({ json: { data: { realName: '数据管理员', avatarAvailable: false } } }))
  await page.route('**/api/v1/notifications**', route => route.fulfill({ json: { data: { records: [], total: 0, pageNo: 1, pageSize: 50 } } }))
  await page.route('**/api/v1/platform/ui-settings', route => route.fulfill({ json: { data: { mouseFollowerEnabled: false } } }))

  await page.route('**/api/v1/admin/operations/data-dictionary/overview**', async route => {
    fixture.overviewCalls.push(route.request().url())
    await route.fulfill({ json: { data: {
      tableCount: tables.length,
      columnCount: tables.reduce((total, table) => total + table.columnCount, 0),
      sensitiveColumnCount: tables.reduce((total, table) => total + table.sensitiveColumnCount, 0),
      indexCount: tables.reduce((total, table) => total + table.indexCount, 0),
      foreignKeyCount: tables.reduce((total, table) => total + table.foreignKeyCount, 0),
      schemaFingerprint: 'sha256:e2e',
      latestFlywayVersion: 'V47',
      generatedAt: '2026-08-21T08:00:00Z',
    } } })
  })

  await page.route('**/api/v1/admin/operations/data-dictionary/tables**', async route => {
    fixture.tableCalls.push(route.request().url())
    if (fixture.failTablesOnce && fixture.tableCalls.length === 1) {
      await route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ message: '数据字典服务暂时不可用' }) })
      return
    }
    const url = new URL(route.request().url())
    const search = url.searchParams.get('keyword') ?? ''
    const tableType = url.searchParams.get('tableType') ?? ''
    const sensitiveOnly = url.searchParams.get('sensitiveOnly') ?? ''
    const hasPrimaryKey = url.searchParams.get('hasPrimaryKey') ?? ''
    const hasForeignKey = url.searchParams.get('hasForeignKey') ?? ''
    const filtered = fixture.emptyTables
      ? []
      : tables.filter(table => (!search || `${table.tableName}${table.tableComment}`.includes(search))
        && (!tableType || table.tableType === tableType)
        && (!sensitiveOnly || String(table.sensitiveColumnCount > 0) === sensitiveOnly)
        && (!hasPrimaryKey || String(table.primaryKeyCount > 0) === hasPrimaryKey)
        && (!hasForeignKey || String(table.foreignKeyCount > 0) === hasForeignKey))
    const body = {
      records: filtered,
      items: filtered,
      total: filtered.length,
      pageNo: Number(url.searchParams.get('pageNo') ?? '1'),
      pageSize: Number(url.searchParams.get('pageSize') ?? '10'),
    }
    await route.fulfill({ json: { data: body } })
  })

  await page.route('**/api/v1/admin/operations/data-dictionary/tables/*', async route => {
    fixture.detailCalls.push(route.request().url())
    await route.fulfill({ json: { data: {
      catalog: 'AInterview', tableName: 'user', tableComment: '平台用户、认证和账号状态', tableType: 'TABLE',
      columns, indexes: [{ indexName: 'PRIMARY', name: 'PRIMARY', unique: true, primary: true, columns: ['id'] }], primaryKeys: [{ keyName: 'PRIMARY', keySequence: 1, columnName: 'id' }], uniqueConstraints: [], foreignKeys: [],
      table: { name: 'user', displayName: '用户账号', comment: '平台用户、认证和账号状态', engine: 'InnoDB' },
      fields: columns.map(column => ({ ...column, name: column.columnName, sensitivity: column.sensitive ? 'SENSITIVE' : 'PUBLIC' })),
      relations: [], constraints: [],
      schemaFingerprint: 'sha256:e2e', generatedAt: '2026-08-21T08:00:00Z',
    } } })
  })
}

async function openDictionary(page: Page, fixture: DataDictionaryFixture, path = '/admin/operations/data-dictionary') {
  await mockSession(page)
  await mockAdminApis(page, fixture)
  await page.goto(path)
  await expect(page.getByRole('heading', { name: /数据字典/ })).toBeVisible()
}

test('ADMIN 可从运维导航进入数据字典，并展示概览、表清单、字段详情和脱敏标记', async ({ page }) => {
  const fixture: DataDictionaryFixture = { overviewCalls: [], tableCalls: [], detailCalls: [] }
  await openDictionary(page, fixture)

  await expect(page.getByRole('link', { name: '数据字典' })).toBeVisible()
  await expect(page.getByText('user', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('company', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('V47', { exact: true })).toBeVisible()
  await expect(page.getByText(/敏感|脱敏/).first()).toBeVisible()
  expect(fixture.overviewCalls.length).toBeGreaterThan(0)
  expect(fixture.tableCalls.length).toBeGreaterThan(0)

  const tableTrigger = page.locator('a,button,[role="button"]').filter({ hasText: 'user' }).first()
  await expect(tableTrigger).toBeVisible()
  await tableTrigger.click()
  await expect(page.getByText('password_hash', { exact: true })).toBeVisible()
  await expect(page.getByText(/禁止展示|脱敏展示/).first()).toBeVisible()
  expect(fixture.detailCalls.length).toBeGreaterThan(0)
})

test('数据字典搜索、筛选和分页由 URL 参数驱动并传递到 tables API', async ({ page }) => {
  const fixture: DataDictionaryFixture = { overviewCalls: [], tableCalls: [], detailCalls: [] }
  await openDictionary(page, fixture, '/admin/operations/data-dictionary?keyword=user&tableType=TABLE&sensitiveOnly=true&hasPrimaryKey=true&hasForeignKey=false&sortBy=tableName&sortOrder=asc&pageNo=2&pageSize=10')

  await expect.poll(() => fixture.tableCalls.length).toBeGreaterThan(0)
  const firstQuery = new URL(fixture.tableCalls.at(-1) ?? '').searchParams
  expect(firstQuery.get('keyword')).toBe('user')
  expect(firstQuery.get('tableType')).toBe('TABLE')
  expect(firstQuery.get('sensitiveOnly')).toBe('true')
  expect(firstQuery.get('hasPrimaryKey')).toBe('true')
  expect(firstQuery.get('hasForeignKey')).toBe('false')
  expect(firstQuery.get('sortBy')).toBe('tableName')
  expect(firstQuery.get('sortOrder')).toBe('asc')
  expect(firstQuery.get('pageNo')).toBe('2')
  expect(firstQuery.get('pageSize')).toBe('10')
  await expect(page).toHaveURL(/keyword=user&tableType=TABLE&sensitiveOnly=true&hasPrimaryKey=true&hasForeignKey=false&sortBy=tableName&sortOrder=asc&pageNo=2&pageSize=10/)
})

test('数据字典空结果、服务错误可恢复，并且移动端没有横向溢出', async ({ page }) => {
  const fixture: DataDictionaryFixture = { overviewCalls: [], tableCalls: [], detailCalls: [], failTablesOnce: true }
  await openDictionary(page, fixture)

  await expect(page.getByText(/数据字典服务暂时不可用|加载失败/)).toBeVisible()
  const retry = page.getByRole('button', { name: /重试|重新加载|再次加载/ }).first()
  await expect(retry).toBeVisible()
  await retry.click()
  await expect(page.getByText('user', { exact: true }).first()).toBeVisible()
  expect(fixture.tableCalls.length).toBeGreaterThanOrEqual(2)

  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.locator('body')).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
})

test('数据字典无表记录时提供正式空状态', async ({ page }) => {
  const fixture: DataDictionaryFixture = { overviewCalls: [], tableCalls: [], detailCalls: [], emptyTables: true }
  await openDictionary(page, fixture)
  await expect(page.getByText(/暂无数据表|暂无表|没有找到/).first()).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
})
