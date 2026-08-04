import { lazy, Suspense, type ComponentType, type ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import { AiAssistant } from '@/components/ai-assistant'
import { AdminPageShell } from '@/components/admin-page-shell'
import { CandidatePageShell } from '@/components/candidate-page-shell'
import { GlobalMouseFollower } from '@/components/global-mouse-follower'
import { PageTransition } from '@/components/page-transition'
import { profile } from '@/lib/session'

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
const AdminCandidateDetail = lazyPage(() => import('@/pages/admin-candidate-detail').then(module => ({ default: module.AdminCandidateDetail })))
const AdminCandidates = lazyPage(() => import('@/pages/admin-candidates').then(module => ({ default: module.AdminCandidates })))
const AdminInterviewReview = lazyPage(() => import('@/pages/admin-interview-review').then(module => ({ default: module.AdminInterviewReview })))
const AdminInterviews = lazyPage(() => import('@/pages/admin-interviews').then(module => ({ default: module.AdminInterviews })))
const AdminQuestionBanks = lazyPage(() => import('@/pages/admin-question-banks').then(module => ({ default: module.AdminQuestionBanks })))
const AdminQuestions = lazyPage(() => import('@/pages/admin-questions').then(module => ({ default: module.AdminQuestions })))
const AdminPromptTemplates = lazyPage(() => import('@/pages/admin-prompt-templates').then(module => ({ default: module.AdminPromptTemplates })))
const AdminSettings = lazyPage(() => import('@/pages/admin-settings').then(module => ({ default: module.AdminSettings })))
const AdminWorkspace = lazyPage(() => import('@/pages/admin-workspace').then(module => ({ default: module.AdminWorkspace })))
const AdminAlgorithmProblems = lazyPage(() => import('@/pages/admin/AdminAlgorithmProblemsPage').then(module => ({ default: module.AdminAlgorithmProblemsPage })))
const AbilityDashboard = lazyPage(() => import('@/pages/ability-dashboard').then(module => ({ default: module.AbilityDashboard })))
const AlgorithmHomePage = lazyPage(() => import('@/pages/algorithm/AlgorithmHomePage').then(module => ({ default: module.AlgorithmHomePage })))
const ProblemDetailPage = lazyPage(() => import('@/pages/algorithm/ProblemDetailPage').then(module => ({ default: module.ProblemDetailPage })))
const ProblemListPage = lazyPage(() => import('@/pages/algorithm/ProblemListPage').then(module => ({ default: module.ProblemListPage })))
const SubmissionDetailPage = lazyPage(() => import('@/pages/algorithm/SubmissionDetailPage').then(module => ({ default: module.SubmissionDetailPage })))
const SubmissionListPage = lazyPage(() => import('@/pages/algorithm/SubmissionListPage').then(module => ({ default: module.SubmissionListPage })))
const WrongProblemPage = lazyPage(() => import('@/pages/algorithm/WrongProblemPage').then(module => ({ default: module.WrongProblemPage })))
const CandidateLibrary = lazyPage(() => import('@/pages/candidate-library').then(module => ({ default: module.CandidateLibrary })))
const CandidateCalendar = lazyPage(() => import('@/pages/candidate-calendar').then(module => ({ default: module.CandidateCalendar })))
const CandidateLobby = lazyPage(() => import('@/pages/candidate-lobby').then(module => ({ default: module.CandidateLobby })))
const CandidateProfile = lazyPage(() => import('@/pages/candidate-profile').then(module => ({ default: module.CandidateProfile })))
const CandidateReport = lazyPage(() => import('@/pages/candidate-report').then(module => ({ default: module.CandidateReport })))
const CandidateReflections = lazyPage(() => import('@/pages/candidate-reflections').then(module => ({ default: module.CandidateReflections })))
const CandidateWorkspaceOverview = lazyPage(() => import('@/pages/candidate-workspace').then(module => ({ default: module.CandidateWorkspaceOverview })))
const FreeInterview = lazyPage(() => import('@/pages/free-interview').then(module => ({ default: module.FreeInterview })))
const InterviewRoom = lazyPage(() => import('@/pages/interview-room').then(module => ({ default: module.InterviewRoom })))
const LoginPage = lazyPage(() => import('@/pages/login').then(module => ({ default: module.LoginPage })))

function PageFallback() {
  return <div className="grid min-h-dvh place-items-center bg-background"><Loader2 className="h-8 w-8 animate-spin text-muted-foreground" /></div>
}

function ContentFallback() {
  return <div className="grid min-h-[45vh] place-items-center"><Loader2 className="h-6 w-6 animate-spin text-muted-foreground" /></div>
}

function Protected({ children, admin = false }: { children: ReactNode; admin?: boolean }) {
  const current = profile()
  if (!current) return <Navigate to="/login" replace />
  if (admin && !current.roles.includes('ADMIN')) return <Navigate to="/candidate/interviews" replace />
  return <>{children}{!admin && !current.roles.includes('ADMIN') && <AiAssistant />}</>
}

function CandidateWorkspace() {
  return <CandidatePageShell>
    <Routes><Route path="/workspace" element={<CandidateWorkspaceOverview />} /><Route path="/candidate/interviews" element={<CandidateLobby />} /><Route path="/candidate/calendar" element={<CandidateCalendar />} /><Route path="/candidate/reflections" element={<CandidateReflections />} /><Route path="/algorithm" element={<AlgorithmHomePage />} /><Route path="/algorithm/problems" element={<ProblemListPage />} /><Route path="/algorithm/problems/:problemId" element={<ProblemDetailPage />} /><Route path="/algorithm/submissions" element={<SubmissionListPage />} /><Route path="/algorithm/submissions/:submissionId" element={<SubmissionDetailPage />} /><Route path="/algorithm/wrong-problems" element={<WrongProblemPage />} /><Route path="*" element={<CandidateWorkspaceOverview />} /></Routes>
  </CandidatePageShell>
}

function AbilityPage() {
  return <CandidatePageShell><AbilityDashboard /></CandidatePageShell>
}

export function App() {
  return <>
    <GlobalMouseFollower />
    <Suspense fallback={<PageFallback />}>
      <Routes>
        <Route path="/login" element={<PageTransition><LoginPage /></PageTransition>} />
        <Route path="/admin/workspace" element={<Protected admin><AdminPageShell><AdminWorkspace /></AdminPageShell></Protected>} />
        <Route path="/admin/interviews" element={<Protected admin><AdminPageShell><AdminInterviews /></AdminPageShell></Protected>} />
        <Route path="/admin/interviews/:id/review" element={<Protected admin><AdminPageShell><AdminInterviewReview /></AdminPageShell></Protected>} />
        <Route path="/admin/interviews/:id/room" element={<Protected admin><AdminPageShell><AdminInterviewReview /></AdminPageShell></Protected>} />
        <Route path="/admin/question-banks" element={<Protected admin><AdminPageShell><AdminQuestionBanks /></AdminPageShell></Protected>} />
        <Route path="/admin/question-banks/:id" element={<Protected admin><AdminPageShell><AdminQuestions /></AdminPageShell></Protected>} />
        <Route path="/admin/candidates" element={<Protected admin><AdminPageShell><AdminCandidates /></AdminPageShell></Protected>} />
        <Route path="/admin/candidates/:id" element={<Protected admin><AdminPageShell><AdminCandidateDetail /></AdminPageShell></Protected>} />
        <Route path="/admin/settings" element={<Protected admin><AdminPageShell><AdminSettings /></AdminPageShell></Protected>} />
        <Route path="/admin/prompt-templates" element={<Protected admin><AdminPageShell><AdminPromptTemplates /></AdminPageShell></Protected>} />
        <Route path="/admin/ai-generations" element={<Protected admin><AdminPageShell><AdminAiGenerations /></AdminPageShell></Protected>} />
      <Route path="/admin/audit-logs" element={<Protected admin><AdminPageShell><AdminAuditLog /></AdminPageShell></Protected>} />
      <Route path="/admin/algorithm/problems" element={<Protected admin><AdminPageShell><AdminAlgorithmProblems /></AdminPageShell></Protected>} />
      <Route path="/admin" element={<Protected admin><Navigate to="/admin/workspace" replace /></Protected>} />
        <Route path="/candidate/interviews/:id/room" element={<Protected><PageTransition><InterviewRoom /></PageTransition></Protected>} />
        <Route path="/candidate/interviews/:id/report" element={<Protected><PageTransition><CandidateReport /></PageTransition></Protected>} />
        <Route path="/candidate/free-interview" element={<Protected><PageTransition><FreeInterview /></PageTransition></Protected>} />
        <Route path="/candidate/reports" element={<Protected><Navigate to="/reports" replace /></Protected>} />
        <Route path="/reports" element={<Protected><AbilityPage /></Protected>} />
        <Route path="/library" element={<Protected><CandidateLibrary /></Protected>} />
        <Route path="/users" element={<Protected><CandidateProfile /></Protected>} />
        <Route path="/interviews" element={<Navigate to="/candidate/interviews" replace />} />
        <Route path="/" element={<Navigate to="/login" replace />} />
        <Route path="*" element={<Protected><CandidateWorkspace /></Protected>} />
      </Routes>
    </Suspense>
  </>
}
