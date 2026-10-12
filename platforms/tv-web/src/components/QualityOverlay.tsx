import { Fragment, useLayoutEffect, useRef, useState, type CSSProperties } from 'react'
import type { CompositionIcon } from '../composition/compose'
import { boundingRect, SAFE_MARGIN } from '../composition/layout'
import type { NormalizedRect, QuoteQuality, QuoteRating, RunQuality } from '../types'
import { chooseOverlayCorner } from './overlay-placement'
import { verdictColor } from './verdict-colors'

interface QualityOverlayProps {
  /** The admin-chosen runs' assessments, or null while the quality overlay is off. */
  quality: QuoteQuality | null
  /** Rating mode, or null while it is off. */
  rating: QuoteRating | null
  /** Where the quote is on screen; the overlay keeps clear of it and of [icons]. */
  quoteRect: NormalizedRect
  icons: CompositionIcon[]
  stageWidth: number
  stageHeight: number
}

function flagText(flags: string[]): string {
  return flags.map((flag) => flag.toLowerCase().replace(/_/g, ' ')).join(' · ')
}

/** The rating's flags by their labels, as the rating panel shows them. */
function ratingFlagText(rating: QuoteRating): string {
  const labels = new Map(rating.flags.map((flag) => [flag.code, flag.label]))
  return rating.problems.map((code) => labels.get(code) ?? flagText([code])).join(' · ')
}

function verdictLabel(run: RunQuality): string {
  if (run.status === 'succeeded') return run.verdict ?? '—'
  return run.status === 'failed' ? 'failed' : 'not assessed'
}

/**
 * The quality overlay (docs/features/quote-quality.md, "TV quality overlay"): the shown quote's or excerpt's verdict
 * and model problems in each run chosen in the admin, one row per run, then the text's precheck flags. Diagnostic
 * only, so it sits in the corner farthest from the quote and the motif icons. In rating mode ("Human ratings on TV")
 * it starts with your rating of the shown text and a hint for the remote, so the two never compete for a corner. It
 * is measured first, hidden, since how much room it needs depends on the runs and flags it lists.
 */
export function QualityOverlay({ quality, rating, quoteRect, icons, stageWidth, stageHeight }: QualityOverlayProps) {
  const overlayRef = useRef<HTMLDivElement>(null)
  const [size, setSize] = useState<{ width: number; height: number } | null>(null)
  useLayoutEffect(() => {
    const overlay = overlayRef.current
    if (!overlay || stageWidth <= 0 || stageHeight <= 0) return
    // Its size does not depend on the corner: every corner leaves the same room beside the safe margin.
    setSize({ width: overlay.offsetWidth / stageWidth, height: overlay.offsetHeight / stageHeight })
  }, [quality, rating, stageWidth, stageHeight])

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
      {rating && (
        <>
          <span
            className="justify-self-start rounded-full px-[0.6em] py-[0.1em] font-semibold uppercase tracking-wide"
            style={{ backgroundColor: verdictColor(rating.verdict) }}
          >
            {rating.verdict ?? 'unrated'}
          </span>
          <span className="truncate text-neutral-400">you · {rating.runLabel}</span>
          <span>{ratingFlagText(rating)}</span>
        </>
      )}
      {quality?.runs.map((run, index) => (
        <Fragment key={index}>
          <span
            className="justify-self-start rounded-full px-[0.6em] py-[0.1em] font-semibold uppercase tracking-wide"
            style={{ backgroundColor: verdictColor(run.verdict) }}
          >
            {verdictLabel(run)}
          </span>
          <span className="truncate text-neutral-400">{run.runLabel}</span>
          <span>{flagText(run.problems)}</span>
        </Fragment>
      ))}
      {quality && quality.precheckFlags.length > 0 && (
        <span className="col-span-3 text-neutral-300">prechecks: {flagText(quality.precheckFlags)}</span>
      )}
    </div>
  )
}
