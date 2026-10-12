import { Fragment, useLayoutEffect, useRef, useState, type CSSProperties } from 'react'
import type { CompositionIcon } from '../composition/compose'
import type { NormalizedRect, QuoteQuality, RunQuality } from '../types'
import { boundingRect, SAFE_MARGIN } from '../composition/layout'
import { chooseOverlayCorner } from './overlay-placement'

/** Verdict colours, after the admin's verdict badges: green for the best down to red for reject. */
const VERDICT_COLORS: Record<string, string> = {
  excellent: '#15803d',
  good: '#4d7c0f',
  acceptable: '#a16207',
  weak: '#c2410c',
  reject: '#b91c1c',
}
const MUTED_COLOR = '#525252'

interface QualityOverlayProps {
  quality: QuoteQuality
  /** Where the quote is on screen; the overlay keeps clear of it and of [icons]. */
  quoteRect: NormalizedRect
  icons: CompositionIcon[]
  stageWidth: number
  stageHeight: number
}

function flagText(flags: string[]): string {
  return flags.map((flag) => flag.toLowerCase().replace(/_/g, ' ')).join(' · ')
}

function verdictLabel(run: RunQuality): string {
  if (run.status === 'succeeded') return run.verdict ?? '—'
  return run.status === 'failed' ? 'failed' : 'not assessed'
}

/**
 * The quality overlay (docs/features/quote-quality.md, "TV quality overlay"): the shown quote's or excerpt's verdict
 * and model problems in each run chosen in the admin, one row per run, then the text's precheck flags. Diagnostic
 * only, so it sits in the corner farthest from the quote and the motif icons. It is measured first, hidden,
 * since how much room it needs depends on the runs and flags it lists.
 */
export function QualityOverlay({ quality, quoteRect, icons, stageWidth, stageHeight }: QualityOverlayProps) {
  const overlayRef = useRef<HTMLDivElement>(null)
  const [size, setSize] = useState<{ width: number; height: number } | null>(null)
  useLayoutEffect(() => {
    const overlay = overlayRef.current
    if (!overlay || stageWidth <= 0 || stageHeight <= 0) return
    // Its size does not depend on the corner: every corner leaves the same room beside the safe margin.
    setSize({ width: overlay.offsetWidth / stageWidth, height: overlay.offsetHeight / stageHeight })
  }, [quality, stageWidth, stageHeight])

  const obstacles = icons.length > 0 ? [quoteRect, boundingRect(icons.map((icon) => icon.circle))] : [quoteRect]
  const corner = size ? chooseOverlayCorner(size, obstacles) : 'bottom-right'
  const margin = `${SAFE_MARGIN * 100}%`
  const position: CSSProperties = {
    [corner.startsWith('top') ? 'top' : 'bottom']: margin,
    [corner.endsWith('left') ? 'left' : 'right']: margin,
  }

  return (
    <div
      ref={overlayRef}
      className="absolute grid max-w-[45%] grid-cols-[auto_auto_1fr] items-center gap-x-[0.6em] gap-y-[0.3em] rounded-[0.8em] bg-black/60 px-[0.9em] py-[0.5em] text-neutral-100"
      style={{ ...position, fontSize: `${stageHeight * 0.02}px`, visibility: size ? 'visible' : 'hidden' }}
    >
      {quality.runs.map((run, index) => (
        <Fragment key={index}>
          <span
            className="justify-self-start rounded-full px-[0.6em] py-[0.1em] font-semibold uppercase tracking-wide"
            style={{ backgroundColor: run.verdict ? (VERDICT_COLORS[run.verdict] ?? MUTED_COLOR) : MUTED_COLOR }}
          >
            {verdictLabel(run)}
          </span>
          <span className="truncate text-neutral-400">{run.runLabel}</span>
          <span>{flagText(run.problems)}</span>
        </Fragment>
      ))}
      {quality.precheckFlags.length > 0 && (
        <span className="col-span-3 text-neutral-300">prechecks: {flagText(quality.precheckFlags)}</span>
      )}
    </div>
  )
}
