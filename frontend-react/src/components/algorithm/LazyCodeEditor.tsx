import { lazy, Suspense } from 'react'
import type { CodeEditorProps } from './CodeEditor'

const CodeEditor = lazy(() => import('./CodeEditor').then(module => ({ default: module.CodeEditor })))

export function LazyCodeEditor(props: CodeEditorProps) {
  return <Suspense fallback={<div className="grid h-[360px] place-items-center bg-muted/20 text-sm text-muted-foreground">正在加载代码编辑器…</div>}><CodeEditor {...props} /></Suspense>
}
