import { useCallback, useEffect, useRef, useState } from 'react'
import { fetchQuoteVisuals, fetchRandomQuotes, tagImageUrl } from './api'
import { CompositionDebug } from './components/CompositionDebug'
import { QuoteStage } from './components/QuoteStage'
import { composeQuote, compositionImageUrls, type Composition } from './composition/compose'
import { useQuoteCarousel } from './hooks/use-quote-carousel'
import { useStageSize } from './hooks/use-stage-size'
import type { PublicQuote, QuoteVisuals } from './types'

/** Show a layer even if its background hasn't loaded by then, rather than holding the old one. */
const READY_TIMEOUT_MS = 3000

const showDebug = new URLSearchParams(window.location.search).has('debug')

interface VersionedComposition {
  /** Bumped on every re-roll, so the stage cross-fades to the new composition. */
  version: number
  composition: Composition
}

interface Layer {
  key: string
  quote: PublicQuote
  composition: Composition
}

function compose(visuals: QuoteVisuals): Composition {
  return composeQuote(visuals, tagImageUrl, Math.random)
}

function App() {
  const [quotes, setQuotes] = useState<PublicQuote[]>([])
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')
  const [compositions, setCompositions] = useState<VersionedComposition[]>([])
  const [layers, setLayers] = useState<Layer[]>([])
  const preloaded = useRef(new Set<string>())
  const { currentIndex, currentQuote, next, previous } = useQuoteCarousel(quotes)
  const stage = useStageSize()

  useEffect(() => {
    fetchRandomQuotes()
      .then((fetched) => {
        setQuotes(fetched)
        // Composing is cheap arithmetic; doing every quote up front lets their images preload.
        setCompositions(fetched.map((quote) => ({ version: 0, composition: compose(quote.visuals) })))
        setStatus('ready')
      })
      .catch((err) => {
        setStatus('error')
        console.error(err)
      })
  }, [])

  // Preload the neighbours' images, so stepping either way doesn't wait on the network.
  useEffect(() => {
    const count = compositions.length
    if (count === 0) return
    for (const index of [(currentIndex + 1) % count, (currentIndex - 1 + count) % count]) {
      for (const url of compositionImageUrls(compositions[index].composition)) {
        if (preloaded.current.has(url)) continue
        preloaded.current.add(url)
        new Image().src = url
      }
    }
  }, [compositions, currentIndex])

  // A new current composition goes on top of the layer stack. This adjusts state during render, as
  // React recommends for state derived from other state, instead of in an effect.
  const current = compositions[currentIndex]
  const currentKey = current ? `${currentIndex}:${current.version}` : null
  if (currentQuote && current && currentKey && layers[layers.length - 1]?.key !== currentKey) {
    setLayers([...layers.slice(-1), { key: currentKey, quote: currentQuote, composition: current.composition }])
  }

  const reroll = useCallback(() => {
    if (!currentQuote) return
    const index = currentIndex
    fetchQuoteVisuals(currentQuote.id)
      .then((visuals) =>
        setCompositions((previousCompositions) =>
          previousCompositions.map((entry, i) =>
            i === index ? { version: entry.version + 1, composition: compose(visuals) } : entry,
          ),
        ),
      )
      .catch((err) => console.error(err))
  }, [currentIndex, currentQuote])

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'ArrowRight') next()
      if (event.key === 'ArrowLeft') previous()
      if (event.key === 'ArrowUp') reroll()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [next, previous, reroll])

  // Once the newest layer has faded in, the ones under it are hidden and can go.
  const dropCoveredLayers = useCallback(() => setLayers((previousLayers) => previousLayers.slice(-1)), [])
  const topLayer = layers[layers.length - 1]

  return (
    <div className="flex h-full w-full items-center justify-center bg-black text-neutral-100">
      {status === 'loading' && <p className="text-xl text-neutral-400">Loading quotes…</p>}
      {status === 'error' && <p className="text-xl text-neutral-400">Couldn't load quotes.</p>}
      {status === 'ready' && (
        <div className="relative overflow-hidden" style={{ width: stage.width, height: stage.height }}>
          {layers.map((layer) => (
            <StageLayer key={layer.key} layer={layer} stageHeight={stage.height} onShown={dropCoveredLayers} />
          ))}
          {showDebug && topLayer && <CompositionDebug composition={topLayer.composition} />}
        </div>
      )}
    </div>
  )
}

interface StageLayerProps {
  layer: Layer
  stageHeight: number
  onShown: () => void
}

/** A composition that stays invisible until its background is ready, then fades in over the previous one. */
function StageLayer({ layer, stageHeight, onShown }: StageLayerProps) {
  const [ready, setReady] = useState(false)
  const markReady = useCallback(() => setReady(true), [])

  useEffect(() => {
    const timer = setTimeout(markReady, READY_TIMEOUT_MS)
    return () => clearTimeout(timer)
  }, [markReady])

  return (
    <div
      className={`absolute inset-0 ${ready ? 'fade-in' : 'opacity-0'}`}
      onAnimationEnd={(event) => {
        if (event.target === event.currentTarget) onShown()
      }}
    >
      <QuoteStage quote={layer.quote} composition={layer.composition} stageHeight={stageHeight} onReady={markReady} />
    </div>
  )
}

export default App
