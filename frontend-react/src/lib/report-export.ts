function safeFileTitle(value: string) {
  return value
    .replace(/[\\/:*?"<>|]/g, '-')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 80)
}

const PRINTABLE_WIDTH_MM = 198
const PRINTABLE_HEIGHT_MM = 285

function resetPaperScale(paper: HTMLElement | null) {
  if (!paper) return
  paper.style.removeProperty('--report-print-scale')
  paper.style.removeProperty('--report-print-layout-width')
  paper.style.removeProperty('--report-print-layout-height')
  paper.removeAttribute('data-print-scale')
}

function fitPaperToSinglePage() {
  const paper = document.querySelector<HTMLElement>('.report-paper-root')
  if (!paper) return

  paper.style.setProperty('--report-print-scale', '1')
  paper.style.setProperty('--report-print-layout-width', `${PRINTABLE_WIDTH_MM}mm`)
  paper.style.setProperty('--report-print-layout-height', `${PRINTABLE_HEIGHT_MM}mm`)

  const rect = paper.getBoundingClientRect()
  if (!rect.width) return

  const printableHeight = rect.width * (PRINTABLE_HEIGHT_MM / PRINTABLE_WIDTH_MM)
  const contentHeight = Math.max(rect.height, paper.scrollHeight)
  const scale = contentHeight > printableHeight
    ? Math.min(1, (printableHeight / contentHeight) * 0.985)
    : 1
  const normalizedScale = Number.isFinite(scale) && scale > 0 ? scale : 1

  paper.style.setProperty('--report-print-scale', normalizedScale.toFixed(5))
  paper.style.setProperty(
    '--report-print-layout-width',
    `${(PRINTABLE_WIDTH_MM / normalizedScale).toFixed(3)}mm`,
  )
  paper.style.setProperty(
    '--report-print-layout-height',
    `${(PRINTABLE_HEIGHT_MM / normalizedScale).toFixed(3)}mm`,
  )
  paper.setAttribute('data-print-scale', normalizedScale.toFixed(3))

  // Force Chromium to apply the new print layout before it snapshots the page.
  paper.getBoundingClientRect()
}

export function exportReportPdf(title: string) {
  const html = document.documentElement
  const paper = document.querySelector<HTMLElement>('.report-paper-root')
  const previousTitle = document.title
  const nextTitle = safeFileTitle(title) || 'AInterview-评测报告'

  function cleanup() {
    html.classList.remove('print-report-mode')
    resetPaperScale(paper)
    document.title = previousTitle
    window.removeEventListener('beforeprint', fitPaperToSinglePage)
    window.removeEventListener('afterprint', cleanup)
  }

  resetPaperScale(paper)
  document.title = nextTitle
  html.classList.add('print-report-mode')
  window.addEventListener('beforeprint', fitPaperToSinglePage)
  window.addEventListener('afterprint', cleanup)
  window.setTimeout(() => {
    window.print()
    window.setTimeout(cleanup, 1200)
  }, 80)
}
