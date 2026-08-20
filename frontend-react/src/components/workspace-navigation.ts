import {
  Activity,
  BarChart3,
  Bot,
  Building2,
  BookOpen,
  BriefcaseBusiness,
  CalendarDays,
  CalendarRange,
  ClipboardList,
  ClipboardCheck,
  Code2,
  FileCode2,
  FileText,
  History,
  KeyRound,
  LayoutDashboard,
  MessageSquareWarning,
  NotebookPen,
  Palette,
  Radar,
  Settings2,
  ShieldCheck,
  UserCog,
  UserRound,
  UsersRound,
  type LucideIcon,
} from 'lucide-react'

export type WorkspaceAudience = 'candidate' | 'company' | 'admin'

export type WorkspaceDomainKey =
  | 'workbench'
  | 'jobs'
  | 'interviews'
  | 'growth'
  | 'recruitment'
  | 'data'
  | 'organization'
  | 'platform'
  | 'users'
  | 'business'
  | 'content'
  | 'ai'
  | 'operations'

export type WorkspaceRouteItem = {
  path: string
  label: string
  description: string
  icon: LucideIcon
  end?: boolean
}

export type WorkspaceDomain = {
  key: WorkspaceDomainKey
  label: string
  description: string
  path?: string
  items: WorkspaceRouteItem[]
}

export type WorkspaceRouteMetadata = {
  audience: WorkspaceAudience
  domain: WorkspaceDomain
  item?: WorkspaceRouteItem
}

const candidateDomains: WorkspaceDomain[] = [
  {
    key: 'workbench', label: '工作台', description: '面试与训练进度', path: '/workspace',
    items: [{ path: '/workspace', label: '个人总览', description: '查看待办和训练进度', icon: LayoutDashboard, end: true }],
  },
  {
    key: 'jobs', label: '求职', description: '岗位与投递进度', path: '/jobs',
    items: [
      { path: '/jobs', label: '岗位大厅', description: '发现适合你的岗位', icon: BriefcaseBusiness },
      { path: '/applications', label: '我的申请', description: '跟进投递与面试进度', icon: FileText },
      { path: '/resumes', label: '我的简历', description: '管理可投递简历', icon: FileText },
    ],
  },
  {
    key: 'interviews', label: '面试', description: '面试任务与时间安排', path: '/candidate/interviews',
    items: [
      { path: '/candidate/interviews', label: '面试任务', description: '进入面试并查看状态', icon: CalendarDays },
      { path: '/candidate/calendar', label: '面试日历', description: '查看安排与时间提醒', icon: CalendarRange },
    ],
  },
  {
    key: 'growth', label: '能力发展', description: '评测结果与专项训练', path: '/reports',
    items: [
      { path: '/reports', label: '能力报告', description: '查看评测结果与训练方向', icon: BarChart3 },
      { path: '/algorithm', label: '算法练习', description: '练习题目与提交记录', icon: Code2 },
      { path: '/library', label: '专项训练', description: '按目标进行训练', icon: BookOpen },
      { path: '/learning-resources', label: '学习资料', description: '阅读与收藏资料', icon: FileText },
      { path: '/candidate/reflections', label: '面试复盘', description: '沉淀面试心得', icon: NotebookPen },
    ],
  },
]

const companyDomains: WorkspaceDomain[] = [
  {
    key: 'workbench', label: '工作台', description: '招聘进度与待办', path: '/company',
    items: [{ path: '/company', label: '招聘总览', description: '查看岗位、申请和待办', icon: LayoutDashboard, end: true }],
  },
  {
    key: 'recruitment', label: '招聘', description: '岗位与候选人管理', path: '/company/positions',
    items: [
      { path: '/company/positions', label: '岗位管理', description: '创建岗位并提交招聘审批', icon: BriefcaseBusiness },
      { path: '/company/applications', label: '申请管理', description: '处理候选人申请与阶段推进', icon: ClipboardList },
      { path: '/company/talent-pool', label: '人才库', description: '维护候选人和协作记录', icon: UsersRound },
    ],
  },
  {
    key: 'interviews', label: '面试', description: '面试安排与评估',
    path: '/company/interviews',
    items: [
      { path: '/company/interviews', label: '面试管理', description: '统一查看和安排面试', icon: CalendarDays },
      { path: '/company/interviews/calendar', label: '面试日历', description: '按日期查看面试安排', icon: CalendarRange },
    ],
  },
  {
    key: 'data', label: '数据', description: '招聘分析与趋势', path: '/company/analytics',
    items: [
      { path: '/company/analytics', label: '招聘分析', description: '查看阶段漏斗与效率指标', icon: BarChart3, end: true },
      { path: '/company/analytics/positions', label: '岗位效果', description: '对比各岗位招聘表现', icon: Activity },
    ],
  },
  {
    key: 'organization', label: '组织', description: '企业资料与团队权限', path: '/company/team',
    items: [
      { path: '/company/team', label: '团队成员', description: '管理招聘专员和面试官', icon: UsersRound, end: true },
      { path: '/company/settings', label: '企业资料', description: '维护企业公开资料与联系人', icon: Settings2 },
    ],
  },
]

const adminDomains: WorkspaceDomain[] = [
  {
    key: 'platform', label: '平台', description: '平台运行概览', path: '/admin/workspace',
    items: [
      { path: '/admin/workspace', label: '运行总览', description: '查看平台规模与运行状态', icon: LayoutDashboard, end: true },
    ],
  },
  {
    key: 'users', label: '用户', description: '企业、账号、员工与角色', path: '/admin/companies',
    items: [
      { path: '/admin/companies', label: '企业管理', description: '维护企业资料与成员入口', icon: Building2 },
      { path: '/admin/users', label: '用户与账号', description: '管理账号、企业成员与状态', icon: UsersRound },
      { path: '/admin/employees', label: '员工管理', description: '管理平台员工职责与账号状态', icon: UserCog },
      { path: '/admin/candidates', label: '候选人档案', description: '查看候选人业务资料与面试记录', icon: UserRound },
      { path: '/admin/roles', label: '角色与权限', description: '查看角色范围与权限矩阵', icon: KeyRound },
    ],
  },
  {
    key: 'business', label: '业务', description: '招聘与面试运营', path: '/admin/interviews',
    items: [
      { path: '/admin/recruitment/requisitions', label: '招聘审批', description: '审核编制、成本中心与招聘预算', icon: ClipboardCheck },
      { path: '/admin/recruitment', label: '招聘运营', description: '定位跨企业招聘异常', icon: Radar },
      { path: '/admin/interviews', label: '面试运营', description: '安排、回顾与报告', icon: CalendarDays },
    ],
  },
  {
    key: 'content', label: '内容', description: '题库与学习资料', path: '/admin/question-banks',
    items: [
      { path: '/admin/question-banks', label: '题库管理', description: '维护公开面试题库', icon: BookOpen },
      { path: '/admin/learning-resources', label: '学习资料', description: '管理平台学习内容', icon: FileText },
      { path: '/admin/algorithm/problems', label: '算法题目', description: '维护算法练习题', icon: Code2 },
    ],
  },
  {
    key: 'ai', label: 'AI', description: '模型服务与生成规则', path: '/admin/ai-operations',
    items: [
      { path: '/admin/ai-operations', label: 'AI 服务概览', description: '查看模型服务和任务状态', icon: Bot },
      { path: '/admin/ai-governance', label: '招聘 AI 治理', description: '评测门禁、预算与紧急停用', icon: ShieldCheck },
      { path: '/admin/prompt-templates', label: '生成规则版本', description: '管理模型生成规则', icon: FileCode2 },
      { path: '/admin/ai-generations', label: '模型调用记录', description: '查看模型调用与结果状态', icon: Activity },
    ],
  },
  {
    key: 'operations', label: '运维', description: '服务、配置与操作追踪', path: '/admin/operations',
    items: [
      { path: '/admin/operations', label: '运行状态', description: '查看服务状态与处理建议', icon: Activity },
      { path: '/admin/settings', label: '平台设置', description: '维护平台基础配置', icon: Settings2 },
      { path: '/admin/theme-settings', label: '主题设置', description: '管理全局交互动效偏好', icon: Palette },
      { path: '/admin/tickets', label: '服务工单', description: '处理用户服务请求', icon: MessageSquareWarning },
      { path: '/admin/audit-logs', label: '操作审计', description: '追踪平台关键操作', icon: History },
    ],
  },
]

export const workspaceNavigation: Record<WorkspaceAudience, WorkspaceDomain[]> = {
  candidate: candidateDomains,
  company: companyDomains,
  admin: adminDomains,
}

const candidateSecondaryItems: WorkspaceRouteItem[] = [
  { path: '/candidate/tickets', label: '问题反馈', description: '需要帮助时联系我们', icon: MessageSquareWarning },
  { path: '/candidate/settings', label: '账户设置', description: '资料与安全设置', icon: Settings2 },
]

export function domainsFor(audience: WorkspaceAudience) {
  return workspaceNavigation[audience]
}

export function domainForKey(audience: WorkspaceAudience, key: WorkspaceDomainKey) {
  return workspaceNavigation[audience].find(domain => domain.key === key)
}

export function secondaryItemsFor(audience: WorkspaceAudience) {
  return audience === 'candidate' ? candidateSecondaryItems : []
}

export function buildContextualPath(path: string, domain: WorkspaceDomainKey) {
  return `${path}?context=${domain}`
}

function isCandidateAccountPath(pathname: string) {
  return pathname === '/users' || pathname === '/candidate/settings' || pathname.startsWith('/candidate/settings/')
}

function matches(pathname: string, item: WorkspaceRouteItem) {
  return item.end ? pathname === item.path : pathname === item.path || pathname.startsWith(`${item.path}/`)
}

export function matchWorkspaceRoute(audience: WorkspaceAudience, pathname: string, search = ''): WorkspaceRouteMetadata {
  const domains = domainsFor(audience)
  const contextualPath = audience === 'candidate' && (pathname === '/candidate/tickets' || isCandidateAccountPath(pathname))
  const context = contextualPath ? new URLSearchParams(search).get('context') as WorkspaceDomainKey | null : null
  const contextualDomain = context ? domainForKey(audience, context) : undefined
  const candidates = domains.flatMap(domain => domain.items.map(item => ({ domain, item })))
    .concat(secondaryItemsFor(audience).map(item => ({ domain: contextualDomain || domains[0], item })))
    .sort((left, right) => right.item.path.length - left.item.path.length)
  const matched = candidates.find(candidate => matches(pathname, candidate.item))
  const domain = matched?.domain || contextualDomain || domains[0]
  return { audience, domain, item: matched?.item }
}
