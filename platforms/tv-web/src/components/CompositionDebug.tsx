import type { Composition } from '../composition/compose'

const withStyle = (name: string, style: string | null) => `${name}${style ? ` (${style})` : ''}`

/** With `?debug` in the URL: what the composition chose, to judge the algorithm on screen. */
export function CompositionDebug({ composition }: { composition: Composition }) {
  const { debug, text, iconStyle } = composition
  const layout =
    debug.arrangement === 'grid' && debug.quoteLayout ? `grid (asked for ${debug.quoteLayout})` : debug.arrangement
  return (
    <div className="absolute bottom-2 left-2 rounded bg-black/70 px-3 py-2 font-mono text-xs leading-relaxed text-neutral-200">
      <p>mood: {debug.mood ? withStyle(debug.mood, debug.moodStyle) : '— (fallback gradient)'}</p>
      <p>
        motifs:{' '}
        {debug.motifs.length > 0 ? debug.motifs.map((motif) => withStyle(motif.name, motif.style)).join(', ') : '—'}
      </p>
      {debug.dock && iconStyle && (
        <p>
          icons: {debug.dock.corner} {debug.dock.direction} · ring {iconStyle.tone}, contrast{' '}
          {iconStyle.contrast.toFixed(1)} · glass {iconStyle.opacity.toFixed(2)}
        </p>
      )}
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
