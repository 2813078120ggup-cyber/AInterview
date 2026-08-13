import { Check, ExternalLink, Image, Loader2, Save, Settings2 } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { profile } from '@/lib/session'
import { request } from '@/lib/api'
import { formatDateTime } from '@/lib/recruitment'
import type { CompanySettings, CompanySettingsInput } from '@/lib/company'

const blank: CompanySettingsInput = {
  name: '', shortName: '', logoUrl: '', industry: '', companySize: '', city: '', description: '', websiteUrl: '',
  recruitmentContactName: '', recruitmentContactEmail: '', recruitmentContactPhone: '',
}

function errorMessage(reason: unknown) {
  return reason instanceof Error && reason.message ? reason.message : '企业设置暂时不可用，请稍后重试。'
}

export function CompanySettings() {
  const canEdit = profile()?.roles.includes('COMPANY_ADMIN') ?? false
  const [settings, setSettings] = useState<CompanySettings>()
  const [draft, setDraft] = useState<CompanySettingsInput>(blank)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [saved, setSaved] = useState(false)

  const load = useCallback(() => {
    setLoading(true)
    setError('')
    void request<CompanySettings>('/v1/company/settings').then(value => {
      setSettings(value)
      setDraft({
        name: value.name ?? '', shortName: value.shortName ?? '', logoUrl: value.logoUrl ?? '', industry: value.industry ?? '',
        companySize: value.companySize ?? '', city: value.city ?? '', description: value.description ?? '', websiteUrl: value.websiteUrl ?? '',
        recruitmentContactName: value.recruitmentContactName ?? '', recruitmentContactEmail: value.recruitmentContactEmail ?? '',
        recruitmentContactPhone: value.recruitmentContactPhone ?? '',
      })
    }).catch(reason => setError(errorMessage(reason))).finally(() => setLoading(false))
  }, [])

  useEffect(() => { void load() }, [load])

  const update = <K extends keyof CompanySettingsInput>(key: K, value: CompanySettingsInput[K]) => {
    setSaved(false)
    setDraft(previous => ({ ...previous, [key]: value }))
  }

  const save = async (event: React.FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setError('')
    try {
      const value = await request<CompanySettings>('/v1/company/settings', { method: 'PUT', body: JSON.stringify(draft) })
      setSettings(value)
      setDraft(previous => ({ ...previous, ...value }))
      setSaved(true)
    } catch (reason) {
      setError(errorMessage(reason))
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <div className="flex min-h-64 items-center justify-center text-sm text-muted-foreground"><Loader2 className="mr-2 h-5 w-5 animate-spin" />正在读取企业设置…</div>

  return <div className="space-y-6">
    <header className="flex flex-col gap-4 border-b border-border pb-6 sm:flex-row sm:items-end sm:justify-between">
      <div><p className="text-xs font-bold uppercase tracking-[.14em] text-[var(--accent)]">Organization / Profile</p><h1 className="mt-2 text-3xl font-black tracking-[-.05em]">企业设置</h1><p className="mt-2 max-w-2xl text-sm leading-6 text-muted-foreground">维护候选人看到的企业资料，以及 HR 处理招聘沟通时使用的联系人信息。</p></div>
      <Badge tone={canEdit ? 'success' : 'info'}>{canEdit ? '管理员可编辑' : '只读查看'}</Badge>
    </header>

    {error && <div role="alert" className="rounded-2xl border border-[var(--danger)]/40 bg-[var(--danger)]/10 px-4 py-3 text-sm text-foreground">{error}<button type="button" className="ml-3 font-semibold text-[var(--accent)] hover:underline" onClick={load}>重试</button></div>}

    <form className="space-y-6" onSubmit={save}>
      <Card className="p-5 sm:p-7">
        <div className="flex items-start gap-3"><span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-[var(--accent-soft)] text-[var(--accent)]"><Settings2 className="h-5 w-5" /></span><div><h2 className="text-lg font-bold">企业资料</h2><p className="mt-1 text-sm text-muted-foreground">用于企业招聘主页和候选人查看岗位时的企业介绍。</p></div></div>
        <div className="mt-6 grid gap-5 md:grid-cols-2">
          <Field label="企业名称" required value={draft.name} onChange={value => update('name', value)} disabled={!canEdit} />
          <Field label="企业简称" value={draft.shortName ?? ''} onChange={value => update('shortName', value)} disabled={!canEdit} />
          <Field label="行业" value={draft.industry ?? ''} onChange={value => update('industry', value)} disabled={!canEdit} />
          <Field label="规模" value={draft.companySize ?? ''} onChange={value => update('companySize', value)} disabled={!canEdit} placeholder="例如：100–499 人" />
          <Field label="所在城市" value={draft.city ?? ''} onChange={value => update('city', value)} disabled={!canEdit} />
          <Field label="官网" value={draft.websiteUrl ?? ''} onChange={value => update('websiteUrl', value)} disabled={!canEdit} placeholder="https://example.com" type="url" />
          <div className="md:col-span-2"><label className="block text-sm font-semibold">Logo 地址</label><div className="mt-2 flex gap-3"><input className="h-11 min-w-0 flex-1 rounded-xl border border-border bg-background px-3 text-sm outline-none transition focus:border-[var(--accent)] focus:ring-2 focus:ring-[var(--accent)]/20 disabled:opacity-60" value={draft.logoUrl ?? ''} onChange={event => update('logoUrl', event.target.value)} disabled={!canEdit} placeholder="受保护的图片地址" />{draft.logoUrl && <div className="grid h-11 w-11 shrink-0 place-items-center overflow-hidden rounded-xl border border-border bg-muted">{canDisplayImage(draft.logoUrl) ? <img src={draft.logoUrl} alt="企业 Logo" className="h-full w-full object-cover" /> : <Image className="h-5 w-5 text-muted-foreground" />}</div>}</div><p className="mt-2 text-xs text-muted-foreground">当前阶段填写图片地址；文件上传不在本步骤新增。</p></div>
          <div className="md:col-span-2"><label className="block text-sm font-semibold">企业简介</label><textarea className="mt-2 min-h-32 w-full resize-y rounded-xl border border-border bg-background px-3 py-3 text-sm leading-6 outline-none transition focus:border-[var(--accent)] focus:ring-2 focus:ring-[var(--accent)]/20 disabled:opacity-60" value={draft.description ?? ''} onChange={event => update('description', event.target.value)} disabled={!canEdit} placeholder="介绍企业业务、团队和招聘方向" /></div>
        </div>
      </Card>

      <Card className="p-5 sm:p-7">
        <div className="flex items-start gap-3"><span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-[var(--info)] text-foreground"><ExternalLink className="h-5 w-5" /></span><div><h2 className="text-lg font-bold">招聘联系人</h2><p className="mt-1 text-sm text-muted-foreground">用于候选人沟通和线下面试邀请的企业联系人。请勿填写密码或密钥。</p></div></div>
        <div className="mt-6 grid gap-5 md:grid-cols-3"><Field label="联系人姓名" value={draft.recruitmentContactName ?? ''} onChange={value => update('recruitmentContactName', value)} disabled={!canEdit} /><Field label="联系人邮箱" value={draft.recruitmentContactEmail ?? ''} onChange={value => update('recruitmentContactEmail', value)} disabled={!canEdit} type="email" /><Field label="联系人手机号" value={draft.recruitmentContactPhone ?? ''} onChange={value => update('recruitmentContactPhone', value)} disabled={!canEdit} /></div>
      </Card>

      <div className="flex flex-col gap-3 border-t border-border pt-4 sm:flex-row sm:items-center sm:justify-between"><span className="text-xs text-muted-foreground">最后更新：{formatDateTime(settings?.updatedAt)}{saved && <span className="ml-2 inline-flex items-center gap-1 text-[var(--success-foreground)]"><Check className="h-3.5 w-3.5" />已保存</span>}</span>{canEdit && <Button type="submit" disabled={saving || !draft.name.trim()}><Save className="h-4 w-4" />{saving ? '保存中…' : '保存企业资料'}</Button>}</div>
    </form>
  </div>
}

function Field({ label, value, onChange, disabled, required, placeholder, type = 'text' }: { label: string; value: string; onChange: (value: string) => void; disabled?: boolean; required?: boolean; placeholder?: string; type?: string }) {
  return <label className="block text-sm font-semibold">{label}{required && <span className="ml-1 text-[var(--danger)]">*</span>}<input type={type} required={required} className="mt-2 h-11 w-full rounded-xl border border-border bg-background px-3 text-sm font-normal outline-none transition focus:border-[var(--accent)] focus:ring-2 focus:ring-[var(--accent)]/20 disabled:opacity-60" value={value} onChange={event => onChange(event.target.value)} disabled={disabled} placeholder={placeholder} /></label>
}

function canDisplayImage(value: string) {
  try { return ['http:', 'https:'].includes(new URL(value).protocol) } catch { return false }
}
