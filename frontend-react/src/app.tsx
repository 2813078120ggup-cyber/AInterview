import { lazy, Suspense, type ComponentType, type ReactNode } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import { AiAssistant } from '@/components/ai-assistant'
import { AdminPageShell } from '@/components/admin-page-shell'
import { CandidatePageShell } from '@/components/candidate-page-shell'
import { CompanyPageShell } from '@/components/company-page-shell'
import { GlobalMouseFollower } from '@/components/global-mouse-follower'
import { PageTransition } from '@/components/page-transition'
import { Button } from '@/components/ui/button'
import { useAuthSession } from '@/lib/auth-session-context'
import { loginPath, postLoginDestination, workspaceAudienceFor, type WorkspaceAudience } from '@/lib/navigation'
import { usePlatformUiSettings } from '@/lib/platform-ui-settings'

/**
 * 路由级懒加载：每个页面独立的 Suspense 边界，切换菜单时只替换内容区，
 * 外壳（侧边栏/页头）保持不动，避免整页白屏的“刷新”观感。
 */
const lazyPage = (factory: () => Promise<{ default: ComponentType }>): ComponentType => {
  const Inner = lazy(factory)
  return function LazyPage() {
    return <Suspense fallback={<ContentFallback />}><Inner /></Suspense>
  }
}

const AdminAuditLog = lazyPage(() => import('@/pages/admin-audit-log').then(module => ({ default: module.AdminAuditLog })))
const AdminAiGenerations = lazyPage(() => import('@/pages/admin-ai-generations').then(module => ({ default: module.AdminAiGenerations })))
const AdminAiOperations = lazyPage(() => import('@/pages/admin-ai-operations').then(module => ({ default: module.AdminAiOperations })))
const AdminAiGovernance = lazyPage(() => import('@/pages/admin-ai-governance').then(module => ({ default: module.AdminAiGovernance })))
const AdminAiTrace = lazyPage(() => import('@/pages/admin-ai-trace').then(module => ({ default: module.AdminAiTrace })))
const AdminCandidateDetail = lazyPage(() => import('@/pages/admin-candidate-detail').then(module => ({ default: module.AdminCandidateDetail })))
const AdminCandidates = lazyPage(() => import('@/pages/admin-candidates').then(module => ({ default: module.AdminCandidates })))
const AdminCompanies = lazyPage(() => import('@/pages/admin-companies').then(module => ({ default: module.AdminCompanies })))
const AdminCompanyDetail = lazyPage(() => import('@/pages/admin-company-detail').then(module => ({ default: module.AdminCompanyDetail })))
const AdminEmployeeDetail = lazyPage(() => import('@/pages/admin-employee-detail').then(module => ({ default: module.AdminEmployeeDetail })))
const AdminEmployees = lazyPage(() => import('@/pages/admin-employees').then(module => ({ default: module.AdminEmployees })))
const AdminInterviewReview = lazyPage(() => import('@/pages/admin-interview-review').then(module => ({ default: module.AdminInterviewReview })))
const AdminInterviews = lazyPage(() => import('@/pages/admin-interviews').then(module => ({ default: module.AdminInterviews })))
const AdminQuestionBanks = lazyPage(() => import('@/pages/admin-question-banks').then(module => ({ default: module.AdminQuestionBanks })))
const AdminQuestions = lazyPage(() => import('@/pages/admin-questions').then(module => ({ default: module.AdminQuestions })))
const AdminPromptTemplates = lazyPage(() => import('@/pages/admin-prompt-templates').then(module => ({ default: module.AdminPromptTemplates })))
const AdminSettings = lazyPage(() => import('@/pages/admin-settings').then(module => ({ default: module.AdminSettings })))
const AdminThemeSettings = lazyPage(() => import('@/pages/admin-theme-settings').then(module => ({ default: module.AdminThemeSettings })))
const AdminWorkspace = lazyPage(() => import('@/pages/admin-workspace').then(module => ({ default: module.AdminWorkspace })))
const AdminOperations = lazyPage(() => import('@/pages/admin-operations').then(module => ({ default: module.AdminOperations })))
const AdminDataDictionary = lazyPage(() => import('@/pages/admin-data-dictionary').then(module => ({ default: module.AdminDataDictionary })))
const AdminUsers = lazyPage(() => import('@/pages/admin-users').then(module => ({ default: module.AdminUsers })))
const AdminUserDetail = lazyPage(() => import('@/pages/admin-user-detail').then(module => ({ default: module.AdminUserDetail })))
const AdminRoles = lazyPage(() => import('@/pages/admin-roles').then(module => ({ default: module.AdminRoles })))
const AdminRecruitment = lazyPage(() => import('@/pages/admin-recruitment').then(module => ({ default: module.AdminRecruitment })))
const AdminRecruitmentRequisitions = lazyPage(() => import('@/pages/admin-recruitment-requisitions').then(module => ({ default: module.AdminRecruitmentRequisitions })))
const AdminRecruitmentApplicationDetail = lazyPage(() => import('@/pages/admin-recruitment-application-detail').then(module => ({ default: module.AdminRecruitmentApplicationDetail })))
const AdminAlgorithmProblems = lazyPage(() => import('@/pages/admin/AdminAlgorithmProblemsPage').then(module => ({ default: module.AdminAlgorithmProblemsPage })))
const AdminLearningResources = lazyPage(() => import('@/pages/admin-learning-resources').then(module => ({ default: module.AdminLearningResources })))
const AbilityDashboard = lazyPage(() => import('@/pages/ability-dashboard').then(module => ({ default: module.AbilityDashboard })))
const AlgorithmHomePage = lazyPage(() => import('@/pages/algorithm/AlgorithmHomePage').then(module => ({ default: module.AlgorithmHomePage })))
const AlgorithmVisualizerPage = lazyPage(() => import('@/pages/algorithm/AlgorithmVisualizerPage').then(module => ({ default: module.AlgorithmVisualizerPage })))
const AlgorithmVisualizerDetailPage = lazyPage(() => import('@/pages/algorithm/AlgorithmVisualizerDetailPage').then(module => ({ default: module.AlgorithmVisualizerDetailPage })))
const ProblemDetailPage = lazyPage(() => import('@/pages/algorithm/ProblemDetailPage').then(module => ({ default: module.ProblemDetailPage })))
const ProblemListPage = lazyPage(() => import('@/pages/algorithm/ProblemListPage').then(module => ({ default: module.ProblemListPage })))
const SubmissionDetailPage = lazyPage(() => import('@/pages/algorithm/SubmissionDetailPage').then(module => ({ default: module.SubmissionDetailPage })))
const SubmissionListPage = lazyPage(() => import('@/pages/algorithm/SubmissionListPage').then(module => ({ default: module.SubmissionListPage })))
const WrongProblemPage = lazyPage(() => import('@/pages/algorithm/WrongProblemPage').then(module => ({ default: module.WrongProblemPage })))
const CandidateLibrary = lazyPage(() => import('@/pages/candidate-library').then(module => ({ default: module.CandidateLibrary })))
const LearningResources = lazyPage(() => import('@/pages/learning-resources').then(module => ({ default: module.LearningResources })))
const LearningResourceViewer = lazyPage(() => import('@/pages/learning-resource-viewer').then(module => ({ default: module.LearningResourceViewer })))
const CandidateCalendar = lazyPage(() => import('@/pages/candidate-calendar').then(module => ({ default: module.CandidateCalendar })))
const CandidateLobby = lazyPage(() => import('@/pages/candidate-lobby').then(module => ({ default: module.CandidateLobby })))
const CandidateProfile = lazyPage(() => import('@/pages/candidate-profile').then(module => ({ default: module.CandidateProfile })))
const CandidateSecurity = lazyPage(() => import('@/pages/candidate-security').then(module => ({ default: module.CandidateSecurity })))
const CandidateNotificationPreferences = lazyPage(() => import('@/pages/candidate-notification-preferences').then(module => ({ default: module.CandidateNotificationPreferences })))
const CandidateReport = lazyPage(() => import('@/pages/candidate-report').then(module => ({ default: module.CandidateReport })))
const CandidateReflections = lazyPage(() => import('@/pages/candidate-reflections').then(module => ({ default: module.CandidateReflections })))
const CandidateTickets = lazyPage(() => import('@/pages/candidate-tickets').then(module => ({ default: module.CandidateTickets })))
const CandidateTicketCreate = lazyPage(() => import('@/pages/candidate-ticket-create').then(module => ({ default: module.CandidateTicketCreate })))
const CandidateTicketDetail = lazyPage(() => import('@/pages/candidate-ticket-detail').then(module => ({ default: module.CandidateTicketDetail })))
const CandidateWorkspaceOverview = lazyPage(() => import('@/pages/candidate-workspace').then(module => ({ default: module.CandidateWorkspaceOverview })))
const CandidateJobHall = lazyPage(() => import('@/pages/candidate-job-hall').then(module => ({ default: module.CandidateJobHall })))
const CandidateApplications = lazyPage(() => import('@/pages/candidate-applications').then(module => ({ default: module.CandidateApplications })))
const CandidateResumes = lazyPage(() => import('@/pages/candidate-resumes').then(module => ({ default: module.CandidateResumes })))
const CompanyDashboard = lazyPage(() => import('@/pages/company-dashboard').then(module => ({ default: module.CompanyDashboard })))
const CompanyPositions = lazyPage(() => import('@/pages/company-positions').then(module => ({ default: module.CompanyPositions })))
const CompanyPositionDetail = lazyPage(() => import('@/pages/company-position-detail').then(module => ({ default: module.CompanyPositionDetail })))
const CompanyPositionForm = lazyPage(() => import('@/pages/company-position-form').then(module => ({ default: module.CompanyPositionForm })))
const CompanyApplications = lazyPage(() => import('@/pages/company-applications').then(module => ({ default: module.CompanyApplications })))
const CompanyApplicationDetail = lazyPage(() => import('@/pages/company-application-detail').then(module => ({ default: module.CompanyApplicationDetail })))
const CompanyTalentPool = lazyPage(() => import('@/pages/company-talent-pool').then(module => ({ default: module.CompanyTalentPool })))
const CompanyTalentPoolDetail = lazyPage(() => import('@/pages/company-talent-pool-detail').then(module => ({ default: module.CompanyTalentPoolDetail })))
const CompanyInterviews = lazyPage(() => import('@/pages/company-interviews').then(module => ({ default: module.CompanyInterviews })))
const CompanyInterviewDetail = lazyPage(() => import('@/pages/company-interview-detail').then(module => ({ default: module.CompanyInterviewDetail })))
const CompanySettings = lazyPage(() => import('@/pages/company-settings').then(module => ({ default: module.CompanySettings })))
const CompanyTeam = lazyPage(() => import('@/pages/company-team').then(module => ({ default: module.CompanyTeam })))
const CompanyAnalytics = lazyPage(() => import('@/pages/company-analytics').then(module => ({ default: module.CompanyAnalytics })))
const CompanyAnalyticsPositions = lazyPage(() => import('@/pages/company-analytics-positions').then(module => ({ default: module.CompanyAnalyticsPositions })))
const FreeInterview = lazyPage(() => import('@/pages/free-interview').then(module => ({ default: module.FreeInterview })))
const InterviewRoom = lazyPage(() => import('@/pages/interview-room').then(module => ({ default: module.InterviewRoom })))
const LoginPage = lazyPage(() => import('@/pages/login').then(module => ({ default: module.LoginPage })))
const CandidateRegistrationPage = lazyPage(() => import('@/pages/register').then(module => ({ default: module.CandidateRegistrationPage })))
const ForgotPasswordPage = lazyPage(() => import('@/pages/forgot-password').then(module => ({ default: module.ForgotPasswordPage })))
const FeaturesPage = lazyPage(() => import('@/pages/features').then(module => ({ default: module.FeaturesPage })))
const AdminTickets = lazyPage(() => import('@/pages/admin-tickets').then(module => ({ default: module.AdminTickets })))
const AdminTicketDetail = lazyPage(() => import('@/pages/admin-ticket-detail').then(module => ({ default: module.AdminTicketDetail })))

function PageFallback() {
  return <div className="grid min-h-dvh place-items-center bg-background"><Loader2 className="h-8 w-8 animate-spin text-muted-foreground" /></div>
}

function ContentFallback() {
  return <div className="grid min-h-[45vh] place-items-center"><Loader2 className="h-6 w-6 animate-spin text-muted-foreground" /></div>
}

function AuthUnavailable({ retry }: { retry?: () => void | Promise<void> }) {
  return <div className="grid min-h-dvh place-items-center bg-background px-6"><div className="max-w-md rounded-2xl border border-border bg-surface p-8 text-center shadow-sm"><h1 className="text-xl font-semibold">暂时无法确认登录身份</h1><p className="mt-3 text-sm leading-6 text-muted-foreground">请检查网络后重试。为保护不同工作区的数据，身份确认失败时不会打开业务页面。</p>{retry && <Button type="button" className="mt-6" onClick={() => void retry()}>重新验证</Button>}</div></div>
}

function UnavailableWorkspace() {
  return <div className="grid min-h-dvh place-items-center bg-background px-6"><div className="max-w-md rounded-2xl border border-border bg-surface p-8 text-center shadow-sm"><h1 className="text-xl font-semibold">无可用工作区</h1><p className="mt-3 text-sm leading-6 text-muted-foreground">当前账号没有可识别的工作区角色。请联系平台管理员检查账号权限。</p></div></div>
}

function Protected({ children, requiredAudience }: { children: ReactNode; requiredAudience: WorkspaceAudience }) {
  const location = useLocation()
  const { status, user, retry } = useAuthSession()
  const requested = `${location.pathname}${location.search}${location.hash}`
  if (status === 'loading') return <PageFallback />
  if (status === 'error') return <AuthUnavailable retry={retry} />
  if (status !== 'authenticated' || !user) return <Navigate to={loginPath(requested)} replace />
  const audience = workspaceAudienceFor(user.roles)
  if (!audience) return <UnavailableWorkspace />
  if (audience !== requiredAudience) return <Navigate to={postLoginDestination(user.roles)} replace />
  return <>{children}{requiredAudience === 'candidate' && <AiAssistant />}</>
}

function PublicAuthRoute({ children }: { children: ReactNode }) {
  const location = useLocation()
  const { status, user } = useAuthSession()
  if (status === 'loading') return <PageFallback />
  if (status === 'authenticated' && user) {
    if (!workspaceAudienceFor(user.roles)) return <UnavailableWorkspace />
    return <Navigate to={postLoginDestination(user.roles, new URLSearchParams(location.search).get('next'))} replace />
  }
  return <>{children}</>
}

function UnknownRoute() {
  const location = useLocation()
  const { status, user, retry } = useAuthSession()
  const requested = `${location.pathname}${location.search}${location.hash}`
  if (status === 'loading') return <PageFallback />
  if (status === 'error') return <AuthUnavailable retry={retry} />
  if (status !== 'authenticated' || !user) return <Navigate to={loginPath(requested)} replace />
  const audience = workspaceAudienceFor(user.roles)
  if (!audience) return <UnavailableWorkspace />
  return <Navigate to={postLoginDestination(user.roles)} replace />
}

function CandidateWorkspace() {
  return <CandidatePageShell>
    <Routes><Route path="/workspace" element={<CandidateWorkspaceOverview />} /><Route path="/candidate/interviews" element={<CandidateLobby />} /><Route path="/candidate/calendar" element={<CandidateCalendar />} /><Route path="/candidate/reflections" element={<CandidateReflections />} /><Route path="/algorithm" element={<AlgorithmHomePage />} /><Route path="/algorithm/visualizer" element={<AlgorithmVisualizerPage />} /><Route path="/algorithm/visualizer/:algorithmSlug" element={<AlgorithmVisualizerDetailPage />} /><Route path="/algorithm/problems" element={<ProblemListPage />} /><Route path="/algorithm/problems/:problemId" element={<ProblemDetailPage />} /><Route path="/algorithm/submissions" element={<SubmissionListPage />} /><Route path="/algorithm/submissions/:submissionId" element={<SubmissionDetailPage />} /><Route path="/algorithm/wrong-problems" element={<WrongProblemPage />} /><Route path="*" element={<CandidateWorkspaceOverview />} /></Routes>
  </CandidatePageShell>
}

function AbilityPage() {
  return <CandidatePageShell><AbilityDashboard /></CandidatePageShell>
}

function CandidateSettingsRedirect() {
  const location = useLocation()
  return <Navigate to={`/candidate/settings/profile${location.search}`} replace />
}

export function App() {
  const location = useLocation()
  const { settings, loading: uiSettingsLoading } = usePlatformUiSettings()
  const publicAuthPath = location.pathname === '/login' || location.pathname === '/register' || location.pathname === '/forgot-password'
  return <>
    {!uiSettingsLoading && settings.mouseFollowerEnabled && location.pathname !== '/' && location.pathname !== '/features' && !publicAuthPath && <GlobalMouseFollower />}
    <Suspense fallback={<PageFallback />}>
      <Routes>
        <Route path="/login" element={<PublicAuthRoute><LoginPage /></PublicAuthRoute>} />
        <Route path="/register" element={<PublicAuthRoute><CandidateRegistrationPage /></PublicAuthRoute>} />
        <Route path="/forgot-password" element={<PublicAuthRoute><ForgotPasswordPage /></PublicAuthRoute>} />
        <Route path="/features" element={<FeaturesPage />} />
        <Route path="/company" element={<Protected requiredAudience="company"><CompanyPageShell><CompanyDashboard /></CompanyPageShell></Protected>} />
        <Route path="/company/positions/new" element={<Protected requiredAudience="company"><CompanyPageShell><CompanyPositionForm /></CompanyPageShell></Protected>} />
        <Route path="/company/positions/:id/edit" element={<Protected requiredAudience="company"><CompanyPageShell><CompanyPositionForm /></CompanyPageShell></Protected>} />
        <Route path="/company/positions/:id" element={<Protected requiredAudience="company"><CompanyPageShell><CompanyPositionDetail /></CompanyPageShell></Protected>} />
        <Route path="/company/positions" element={<Protected requiredAudience="company"><CompanyPageShell><CompanyPositions /></CompanyPageShell></Protected>} />
        <Route path="/company/applications/:id" element={<Protected requiredAudience="company"><CompanyPageShell><CompanyApplicationDetail /></CompanyPageShell></Protected>} />
        <Route path="/company/applications" element={<Protected requiredAudience="company"><CompanyPageShell><CompanyApplications /></CompanyPageShell></Protected>} />
        <Route path="/company/talent-pool/:candidateId" element={<Protected requiredAudience="company"><CompanyPageShell><CompanyTalentPoolDetail /></CompanyPageShell></Protected>} />
        <Route path="/company/talent-pool" element={<Protected requiredAudience="company"><CompanyPageShell><CompanyTalentPool /></CompanyPageShell></Protected>} />
        <Route path="/company/interviews/calendar" element={<Protected requiredAudience="company"><CompanyPageShell><CompanyInterviews /></CompanyPageShell></Protected>} />
        <Route path="/company/interviews/:id" element={<Protected requiredAudience="company"><CompanyPageShell><CompanyInterviewDetail /></CompanyPageShell></Protected>} />
        <Route path="/company/interviews" element={<Protected requiredAudience="company"><CompanyPageShell><CompanyInterviews /></CompanyPageShell></Protected>} />
        <Route path="/company/settings" element={<Protected requiredAudience="company"><CompanyPageShell><CompanySettings /></CompanyPageShell></Protected>} />
        <Route path="/company/team" element={<Protected requiredAudience="company"><CompanyPageShell><CompanyTeam /></CompanyPageShell></Protected>} />
        <Route path="/company/analytics/positions" element={<Protected requiredAudience="company"><CompanyPageShell><CompanyAnalyticsPositions /></CompanyPageShell></Protected>} />
        <Route path="/company/analytics" element={<Protected requiredAudience="company"><CompanyPageShell><CompanyAnalytics /></CompanyPageShell></Protected>} />
        <Route path="/company/account/security" element={<Protected requiredAudience="company"><CompanyPageShell><CandidateSecurity /></CompanyPageShell></Protected>} />
        <Route path="/admin/workspace" element={<Protected requiredAudience="admin"><AdminPageShell><AdminWorkspace /></AdminPageShell></Protected>} />
        <Route path="/admin/dashboard" element={<Protected requiredAudience="admin"><Navigate to="/admin/workspace" replace /></Protected>} />
        <Route path="/admin/index" element={<Protected requiredAudience="admin"><Navigate to="/admin/workspace" replace /></Protected>} />
        <Route path="/admin/companies/:id" element={<Protected requiredAudience="admin"><AdminPageShell><AdminCompanyDetail /></AdminPageShell></Protected>} />
        <Route path="/admin/companies" element={<Protected requiredAudience="admin"><AdminPageShell><AdminCompanies /></AdminPageShell></Protected>} />
        <Route path="/admin/users/:id" element={<Protected requiredAudience="admin"><AdminPageShell><AdminUserDetail /></AdminPageShell></Protected>} />
        <Route path="/admin/users" element={<Protected requiredAudience="admin"><AdminPageShell><AdminUsers /></AdminPageShell></Protected>} />
        <Route path="/admin/employees/:id" element={<Protected requiredAudience="admin"><AdminPageShell><AdminEmployeeDetail /></AdminPageShell></Protected>} />
        <Route path="/admin/employees" element={<Protected requiredAudience="admin"><AdminPageShell><AdminEmployees /></AdminPageShell></Protected>} />
        <Route path="/admin/roles" element={<Protected requiredAudience="admin"><AdminPageShell><AdminRoles /></AdminPageShell></Protected>} />
        <Route path="/admin/recruitment/applications/:id" element={<Protected requiredAudience="admin"><AdminPageShell><AdminRecruitmentApplicationDetail /></AdminPageShell></Protected>} />
        <Route path="/admin/recruitment/requisitions" element={<Protected requiredAudience="admin"><AdminPageShell><AdminRecruitmentRequisitions /></AdminPageShell></Protected>} />
        <Route path="/admin/recruitment" element={<Protected requiredAudience="admin"><AdminPageShell><AdminRecruitment /></AdminPageShell></Protected>} />
        <Route path="/admin/ai-operations/traces/generations/:id" element={<Protected requiredAudience="admin"><AdminPageShell><AdminAiTrace /></AdminPageShell></Protected>} />
        <Route path="/admin/ai-operations" element={<Protected requiredAudience="admin"><AdminPageShell><AdminAiOperations /></AdminPageShell></Protected>} />
        <Route path="/admin/ai-governance" element={<Protected requiredAudience="admin"><AdminPageShell><AdminAiGovernance /></AdminPageShell></Protected>} />
        <Route path="/admin/ai" element={<Protected requiredAudience="admin"><Navigate to="/admin/ai-operations" replace /></Protected>} />
        <Route path="/admin/interviews" element={<Protected requiredAudience="admin"><AdminPageShell><AdminInterviews /></AdminPageShell></Protected>} />
        <Route path="/admin/interviews/:id/review" element={<Protected requiredAudience="admin"><AdminPageShell><AdminInterviewReview /></AdminPageShell></Protected>} />
        <Route path="/admin/interviews/:id/room" element={<Protected requiredAudience="admin"><AdminPageShell><AdminInterviewReview /></AdminPageShell></Protected>} />
        <Route path="/admin/tickets" element={<Protected requiredAudience="admin"><AdminPageShell><AdminTickets /></AdminPageShell></Protected>} />
        <Route path="/admin/tickets/:id" element={<Protected requiredAudience="admin"><AdminPageShell><AdminTicketDetail /></AdminPageShell></Protected>} />
        <Route path="/admin/question-banks" element={<Protected requiredAudience="admin"><AdminPageShell><AdminQuestionBanks /></AdminPageShell></Protected>} />
        <Route path="/admin/question-banks/:id" element={<Protected requiredAudience="admin"><AdminPageShell><AdminQuestions /></AdminPageShell></Protected>} />
        <Route path="/admin/candidates" element={<Protected requiredAudience="admin"><AdminPageShell><AdminCandidates /></AdminPageShell></Protected>} />
        <Route path="/admin/candidates/:id" element={<Protected requiredAudience="admin"><AdminPageShell><AdminCandidateDetail /></AdminPageShell></Protected>} />
        <Route path="/admin/settings" element={<Protected requiredAudience="admin"><AdminPageShell><AdminSettings /></AdminPageShell></Protected>} />
        <Route path="/admin/theme-settings" element={<Protected requiredAudience="admin"><AdminPageShell><AdminThemeSettings /></AdminPageShell></Protected>} />
        <Route path="/admin/prompt-templates" element={<Protected requiredAudience="admin"><AdminPageShell><AdminPromptTemplates /></AdminPageShell></Protected>} />
        <Route path="/admin/ai-generations" element={<Protected requiredAudience="admin"><AdminPageShell><AdminAiGenerations /></AdminPageShell></Protected>} />
        <Route path="/admin/audit-logs" element={<Protected requiredAudience="admin"><AdminPageShell><AdminAuditLog /></AdminPageShell></Protected>} />
        <Route path="/admin/operations" element={<Protected requiredAudience="admin"><AdminPageShell><AdminOperations /></AdminPageShell></Protected>} />
        <Route path="/admin/operations/data-dictionary" element={<Protected requiredAudience="admin"><AdminPageShell><AdminDataDictionary /></AdminPageShell></Protected>} />
        <Route path="/admin/algorithm/problems" element={<Protected requiredAudience="admin"><AdminPageShell><AdminAlgorithmProblems /></AdminPageShell></Protected>} />
        <Route path="/admin/learning-resources" element={<Protected requiredAudience="admin"><AdminPageShell><AdminLearningResources /></AdminPageShell></Protected>} />
        <Route path="/admin/learning-resources/:publicId" element={<Protected requiredAudience="admin"><AdminPageShell><LearningResourceViewer /></AdminPageShell></Protected>} />
        <Route path="/admin/account/security" element={<Protected requiredAudience="admin"><AdminPageShell><CandidateSecurity /></AdminPageShell></Protected>} />
        <Route path="/admin" element={<Protected requiredAudience="admin"><Navigate to="/admin/workspace" replace /></Protected>} />
        <Route path="/candidate/interviews/:id/room" element={<Protected requiredAudience="candidate"><PageTransition><InterviewRoom /></PageTransition></Protected>} />
        <Route path="/candidate/interviews/:id/report" element={<Protected requiredAudience="candidate"><PageTransition><CandidateReport /></PageTransition></Protected>} />
        <Route path="/candidate/free-interview" element={<Protected requiredAudience="candidate"><PageTransition><FreeInterview /></PageTransition></Protected>} />
        <Route path="/jobs" element={<Protected requiredAudience="candidate"><CandidatePageShell><CandidateJobHall /></CandidatePageShell></Protected>} />
        <Route path="/applications" element={<Protected requiredAudience="candidate"><CandidatePageShell><CandidateApplications /></CandidatePageShell></Protected>} />
        <Route path="/resumes" element={<Protected requiredAudience="candidate"><CandidatePageShell><CandidateResumes /></CandidatePageShell></Protected>} />
        <Route path="/candidate/tickets" element={<Protected requiredAudience="candidate"><CandidatePageShell><CandidateTickets /></CandidatePageShell></Protected>} />
        <Route path="/candidate/tickets/new" element={<Protected requiredAudience="candidate"><CandidatePageShell><CandidateTicketCreate /></CandidatePageShell></Protected>} />
        <Route path="/candidate/tickets/:id/edit" element={<Protected requiredAudience="candidate"><CandidatePageShell><CandidateTicketCreate /></CandidatePageShell></Protected>} />
        <Route path="/candidate/tickets/:id" element={<Protected requiredAudience="candidate"><CandidatePageShell><CandidateTicketDetail /></CandidatePageShell></Protected>} />
        <Route path="/candidate/reports" element={<Protected requiredAudience="candidate"><Navigate to="/reports" replace /></Protected>} />
        <Route path="/reports" element={<Protected requiredAudience="candidate"><AbilityPage /></Protected>} />
        <Route path="/library" element={<Protected requiredAudience="candidate"><CandidateLibrary /></Protected>} />
        <Route path="/learning-resources" element={<Protected requiredAudience="candidate"><CandidatePageShell><LearningResources /></CandidatePageShell></Protected>} />
        <Route path="/learning-resources/:publicId" element={<Protected requiredAudience="candidate"><CandidatePageShell><LearningResourceViewer /></CandidatePageShell></Protected>} />
        <Route path="/candidate/settings/profile" element={<Protected requiredAudience="candidate"><CandidatePageShell><CandidateProfile /></CandidatePageShell></Protected>} />
        <Route path="/candidate/settings/security" element={<Protected requiredAudience="candidate"><CandidatePageShell><CandidateSecurity /></CandidatePageShell></Protected>} />
        <Route path="/candidate/settings/notifications" element={<Protected requiredAudience="candidate"><CandidatePageShell><CandidateNotificationPreferences /></CandidatePageShell></Protected>} />
        <Route path="/candidate/settings" element={<Protected requiredAudience="candidate"><CandidateSettingsRedirect /></Protected>} />
        <Route path="/users" element={<Protected requiredAudience="candidate"><CandidateSettingsRedirect /></Protected>} />
        <Route path="/interviews" element={<Protected requiredAudience="candidate"><Navigate to="/candidate/interviews" replace /></Protected>} />
        <Route path="/workspace" element={<Protected requiredAudience="candidate"><CandidateWorkspace /></Protected>} />
        <Route path="/candidate/interviews" element={<Protected requiredAudience="candidate"><CandidateWorkspace /></Protected>} />
        <Route path="/candidate/calendar" element={<Protected requiredAudience="candidate"><CandidateWorkspace /></Protected>} />
        <Route path="/candidate/reflections" element={<Protected requiredAudience="candidate"><CandidateWorkspace /></Protected>} />
        <Route path="/algorithm" element={<Protected requiredAudience="candidate"><CandidateWorkspace /></Protected>} />
        <Route path="/algorithm/visualizer" element={<Protected requiredAudience="candidate"><CandidateWorkspace /></Protected>} />
        <Route path="/algorithm/visualizer/:algorithmSlug" element={<Protected requiredAudience="candidate"><CandidateWorkspace /></Protected>} />
        <Route path="/algorithm/problems" element={<Protected requiredAudience="candidate"><CandidateWorkspace /></Protected>} />
        <Route path="/algorithm/problems/:problemId" element={<Protected requiredAudience="candidate"><CandidateWorkspace /></Protected>} />
        <Route path="/algorithm/submissions" element={<Protected requiredAudience="candidate"><CandidateWorkspace /></Protected>} />
        <Route path="/algorithm/submissions/:submissionId" element={<Protected requiredAudience="candidate"><CandidateWorkspace /></Protected>} />
        <Route path="/algorithm/wrong-problems" element={<Protected requiredAudience="candidate"><CandidateWorkspace /></Protected>} />
        <Route path="/" element={<FeaturesPage />} />
        <Route path="*" element={<UnknownRoute />} />
      </Routes>
    </Suspense>
  </>
}
