import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'

export function Reveal({ children, className, delay = 0 }: { children: ReactNode; className?: string; delay?: number }) {
  const ref = useRef<HTMLDivElement>(null)
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    const node = ref.current
    if (!node || typeof IntersectionObserver === 'undefined') {
      setVisible(true)
      return
    }
    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) {
        setVisible(true)
        observer.disconnect()
      }
    }, { threshold: 0.14 })
    observer.observe(node)
    return () => observer.disconnect()
  }, [])

  return <div ref={ref} className={cn('features-reveal', visible && 'is-visible', className)} style={{ '--reveal-delay': `${delay}ms` } as CSSProperties}>{children}</div>
}

export function FeatureHeading({ eyebrow, title, description, align = 'left', id }: { eyebrow?: string; title: string; description: string; align?: 'left' | 'center'; id?: string }) {
  return <div className={cn('features-heading', align === 'center' && 'features-heading-center')}>
    {eyebrow && <p className="features-eyebrow">{eyebrow}</p>}
    <h2 id={id}>{title}</h2>
    <p className="features-heading-copy">{description}</p>
  </div>
}

export function ProductWindow({ children, title, subtitle, className, activeTab = '概览' }: { children: ReactNode; title: string; subtitle?: string; className?: string; activeTab?: string }) {
  return <section className={cn('product-window', className)} aria-label={`${title} 功能演示`}>
    <div className="product-window-topbar">
      <div className="product-window-controls" aria-hidden="true"><span /><span /><span /></div>
      <div className="product-window-title"><strong>{title}</strong>{subtitle && <span>{subtitle}</span>}</div>
      <span className="product-window-live"><span className="product-window-live-dot" />交互预览</span>
    </div>
    <div className="product-window-tabs" aria-hidden="true">
      <span className="product-window-tab-brand">AInterview</span>
      {['概览', activeTab].filter((tab, index, list) => list.indexOf(tab) === index).map(tab => <span key={tab} className={tab === activeTab ? 'is-active' : ''}>{tab}</span>)}
    </div>
    <div className="product-window-body">{children}</div>
  </section>
}

export function StatusBadge({ children, tone = 'default' }: { children: ReactNode; tone?: 'default' | 'success' | 'warning' | 'danger' | 'info' }) {
  return <Badge tone={tone} className="features-status-badge">{children}</Badge>
}

export function ScoreRing({ score, label = '匹配度', size = 112, className }: { score: number; label?: string; size?: number; className?: string }) {
  const radius = 42
  const circumference = 2 * Math.PI * radius
  return <div className={cn('score-ring', className)} style={{ width: size, height: size }} aria-label={`${label} ${score}%`} role="img">
    <svg viewBox="0 0 100 100" aria-hidden="true">
      <circle className="score-ring-track" cx="50" cy="50" r={radius} />
      <circle className="score-ring-value" cx="50" cy="50" r={radius} strokeDasharray={circumference} strokeDashoffset={circumference - circumference * score / 100} />
    </svg>
    <span><strong>{score}%</strong><small>{label}</small></span>
  </div>
}

export function MiniBars({ values }: { values: Array<[string, number]> }) {
  return <div className="mini-bars" aria-label="能力维度分布">
    {values.map(([label, value]) => <div key={label} className="mini-bars-row"><span>{label}</span><i><b style={{ width: `${value}%` }} /></i><strong>{value}</strong></div>)}
  </div>
}
