import type { Composition } from '../composition/compose'

const withStyle = (name: string | null, style: string | null, none: string) =>
  name ? `${name}${style ? ` (${style})` : ''}` : none

/** With `?debug` in the URL: what the composition chose, to judge the algorithm on screen. */
export function CompositionDebug({ composition }: { composition: Composition }) {
  const { debug, text } = composition
  const layout =
    debug.arrangement === 'grid' && debug.quoteLayout ? `grid (asked for ${debug.quoteLayout})` : debug.arrangement
  return (
    <div className="absolute bottom-2 left-2 rounded bg-black/70 px-3 py-2 font-mono text-xs leading-relaxed text-neutral-200">
      <p>mood: {withStyle(debug.mood, debug.moodStyle, '— (fallback gradient)')}</p>
      <p>
        motif: {withStyle(debug.motif, debug.motifStyle, '—')}
        {debug.styleFit !== null ? ` · fit ${debug.styleFit}` : ''}
      </p>
      <p>
        layout: {layout} · text: {text.tone}, contrast {text.contrast.toFixed(1)}
        {text.textShadow && !text.backdrop ? ', halo' : ''}
      </p>
      <p>
        {text.backdrop
          ? `backdrop ${text.backdrop.opacity.toFixed(2)} (${text.backdrop.reason})`
          : 'no backdrop (quiet)'}{' '}
        · detail {text.meanDetail.toFixed(3)}, peak {text.peakDetail.toFixed(3)}
      </p>
      <p className="text-neutral-400">↑ re-roll · ← → previous/next</p>
    </div>
  )
}
