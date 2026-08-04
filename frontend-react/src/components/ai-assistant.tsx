import { BrainCircuit, MessageCircleMore, SendHorizontal, X } from 'lucide-react'
import { type FormEvent, useState } from 'react'
import { request } from '@/lib/api'

type Message = { role: 'assistant' | 'user'; content: string }

export function AiAssistant() {
  const [open, setOpen] = useState(false)
  const [messages, setMessages] = useState<Message[]>([
    { role: 'assistant', content: '我是 AI 面试教练，可提供回答结构、项目表达和模拟追问建议。' },
  ])
  const [draft, setDraft] = useState('')
  const [busy, setBusy] = useState(false)

  async function send(event: FormEvent) {
    event.preventDefault()
    const content = draft.trim()
    if (!content || busy) return
    const next = [...messages, { role: 'user' as const, content }]
    setMessages(next)
    setDraft('')
    setBusy(true)
    try {
      const result = await request<{ reply: string }>('/v1/ai-assistant/chat', {
        method: 'POST',
        body: JSON.stringify({ messages: next.map(item => ({ role: item.role, content: item.content })) }),
      })
      setMessages([...next, { role: 'assistant', content: result.reply }])
    } catch (error) {
      setMessages([...next, {
        role: 'assistant',
        content: error instanceof Error ? error.message : '服务暂不可用，请稍后重试。',
      }])
    } finally {
      setBusy(false)
    }
  }

  return <div className="fixed bottom-4 right-4 z-[60] sm:bottom-6 sm:right-6">
    <button
      type="button"
      onClick={() => setOpen(value => !value)}
      className="grid h-14 w-14 place-items-center rounded-2xl bg-[var(--primary)] text-white shadow-[0_12px_30px_rgba(21,20,18,.18)] transition hover:-translate-y-1 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent)] focus-visible:ring-offset-2"
      aria-label={open ? '关闭 AI 面试教练' : '打开 AI 面试教练'}
      aria-expanded={open}
    >
      {open ? <X className="h-5 w-5" /> : <MessageCircleMore className="h-5 w-5" />}
    </button>
    {open && <section aria-label="AI 面试教练" className="absolute bottom-16 right-0 flex h-[min(560px,calc(100dvh-7rem))] w-[min(380px,calc(100vw-32px))] flex-col overflow-hidden rounded-[24px] border border-border bg-surface shadow-2xl">
      <header className="soft-emphasis-panel flex items-center gap-3 border-b border-border px-5 py-4">
        <span className="grid h-9 w-9 place-items-center rounded-xl bg-[var(--surface)]/70"><BrainCircuit className="h-5 w-5 text-[var(--accent)]" /></span>
        <div><strong className="text-sm">AI 面试教练</strong><p className="mt-0.5 text-xs text-white/70">由 DeepSeek 提供支持</p></div>
      </header>
      <div className="flex-1 space-y-3 overflow-y-auto p-4" aria-live="polite">
        {messages.map((item, index) => <article key={index} className={`max-w-[90%] rounded-2xl px-3 py-2.5 text-sm leading-6 ${item.role === 'user' ? 'ml-auto bg-[var(--primary)] text-white' : 'bg-muted text-foreground'}`}>{item.content}</article>)}
        {busy && <p className="w-fit rounded-2xl bg-muted px-3 py-2 text-sm text-muted-foreground">正在生成建议…</p>}
      </div>
      <form onSubmit={send} className="border-t border-border p-3">
        <label className="sr-only" htmlFor="ai-coach-question">咨询内容</label>
        <div className="flex gap-2">
          <input id="ai-coach-question" value={draft} onChange={event => setDraft(event.target.value)} className="h-11 min-w-0 flex-1 rounded-xl border border-border bg-background px-3 text-sm outline-none focus:border-[var(--accent)]" placeholder="例如：如何说明项目难点？" />
          <button disabled={busy || !draft.trim()} aria-label="发送咨询" className="grid h-11 w-11 place-items-center rounded-xl bg-[var(--primary)] text-white disabled:cursor-not-allowed disabled:opacity-50"><SendHorizontal className="h-4 w-4" /></button>
        </div>
      </form>
    </section>}
  </div>
}
