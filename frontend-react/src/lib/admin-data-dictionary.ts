import { request } from '@/lib/api'

/** 数据字典由数据库元数据只读生成，前端不暴露建表或写入入口。 */
export type DataDictionaryTableType = 'TABLE' | 'VIEW' | 'SYSTEM' | string
export type DataDictionarySensitivity = 'PUBLIC' | 'INTERNAL' | 'SENSITIVE' | 'HIGHLY_SENSITIVE' | string

export type AdminDataDictionaryOverview = {
  catalog: string
  tableCount: number
  columnCount: number
  sensitiveColumnCount: number
  indexCount?: number
  foreignKeyCount?: number
  schemaFingerprint: string
  latestFlywayVersion?: string | null
  generatedAt: string
}

export type AdminDataDictionaryTable = {
  tableName: string
  tableComment: string
  tableType: DataDictionaryTableType
  columnCount: number
  sensitiveColumnCount: number
  indexCount: number
  foreignKeyCount: number
  hasPrimaryKey: boolean
  hasForeignKey: boolean
  primaryKeyColumns: string[]
}

export type AdminDataDictionaryPage = {
  records: AdminDataDictionaryTable[]
  total: number
  pageNo: number
  pageSize: number
}

export type AdminDataDictionaryField = {
  ordinalPosition: number
  columnName: string
  dataType: string
  nativeType: string
  length?: number | null
  scale?: number | null
  nullable: boolean
  defaultValue?: string | null
  maskedDefaultValue?: string | null
  defaultValueMasked: boolean
  autoIncrement: boolean
  comment?: string | null
  sensitive: boolean
  maskingDefault?: string | null
}

export type AdminDataDictionaryIndex = {
  indexName: string
  columns: string[]
  unique: boolean
  primary: boolean
}

export type AdminDataDictionaryConstraint = {
  constraintName: string
  columns: string[]
}

export type AdminDataDictionaryRelation = {
  constraintName: string
  keySequence: number
  columnName: string
  referencedCatalog: string
  referencedTable: string
  referencedColumn: string
  updateRule: string
  deleteRule: string
}

export type AdminDataDictionaryDetail = {
  catalog: string
  tableName: string
  tableComment: string
  tableType: DataDictionaryTableType
  columns: AdminDataDictionaryField[]
  indexes: AdminDataDictionaryIndex[]
  primaryKeys: { keyName: string; keySequence: number; columnName: string }[]
  uniqueConstraints: AdminDataDictionaryConstraint[]
  foreignKeys: AdminDataDictionaryRelation[]
  schemaFingerprint: string
  generatedAt: string
}

export type AdminDataDictionaryQuery = {
  pageNo?: number
  pageSize?: number
  keyword?: string
  sortBy?: string
  sortOrder?: string
  tableType?: string
  sensitiveOnly?: boolean
  hasPrimaryKey?: boolean
  hasForeignKey?: boolean
}

function queryString(query: AdminDataDictionaryQuery = {}) {
  const params = new URLSearchParams()
  if (query.pageNo != null) params.set('pageNo', String(query.pageNo))
  if (query.pageSize != null) params.set('pageSize', String(query.pageSize))
  if (query.keyword?.trim()) params.set('keyword', query.keyword.trim())
  if (query.tableType) params.set('tableType', query.tableType)
  if (query.sortBy) params.set('sortBy', query.sortBy)
  if (query.sortOrder) params.set('sortOrder', query.sortOrder)
  if (query.sensitiveOnly != null) params.set('sensitiveOnly', String(query.sensitiveOnly))
  if (query.hasPrimaryKey != null) params.set('hasPrimaryKey', String(query.hasPrimaryKey))
  if (query.hasForeignKey != null) params.set('hasForeignKey', String(query.hasForeignKey))
  const value = params.toString()
  return value ? `?${value}` : ''
}

export function getAdminDataDictionary(query: AdminDataDictionaryQuery = {}) {
  return request<AdminDataDictionaryPage>(`/v1/admin/operations/data-dictionary/tables${queryString(query)}`)
}

export function getAdminDataDictionaryOverview() {
  return request<AdminDataDictionaryOverview>('/v1/admin/operations/data-dictionary/overview')
}

export function getAdminDataDictionaryDetail(tableName: string) {
  return request<AdminDataDictionaryDetail>(`/v1/admin/operations/data-dictionary/tables/${encodeURIComponent(tableName)}`)
}
