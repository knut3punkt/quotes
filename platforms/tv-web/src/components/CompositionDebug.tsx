import type { Composition } from '../composition/compose'

/** With `?debug` in the URL: what the composition chose, to judge the algorithm on screen. */
export function CompositionDebug({ composition }: { composition: Composition }) {
  const { debug, text } = composition
  return (
    <div className="absolute bottom-2 left-2 rounded bg-black/70 px-3 py-2 font-mono text-xs leading-relaxed text-neutral-200">
      <p>mood: {debug.mood ?? '— (fallback gradient)'}</p>
      <p>motifs: {debug.motifs.length > 0 ? debug.motifs.join(', ') : '—'}</p>
      <p>
        layout: {debug.arrangement} · text: {text.tone}, contrast {text.contrast.toFixed(1)}
        {text.textShadow ? ', halo' : ''}
        {text.scrim ? `, scrim ${text.scrim.opacity.toFixed(2)}` : ''}
      </p>
      <p className="text-neutral-400">↑ re-roll · ← → previous/next</p>
    </div>
  )
}
