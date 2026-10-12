import { useCallback, useEffect, useRef, useState, type WheelEvent } from 'react'
import {
  createQualityFlag,
  fetchQuoteQuality,
  fetchQuoteRating,
  fetchQuoteVisuals,
  fetchRandomQuotes,
  saveQuoteRating,
  tagImageUrl,
} from './api'
import { CompositionDebug } from './components/CompositionDebug'
import { QualityOverlay } from './components/QualityOverlay'
import { QuoteStage } from './components/QuoteStage'
import { RatingPanel } from './components/RatingPanel'
import { Toast, type ToastMessage } from './components/Toast'
import { composeQuote, compositionImageUrls, type Composition } from './composition/compose'
import { useQuoteCarousel } from './hooks/use-quote-carousel'
import { useStageSize } from './hooks/use-stage-size'
import {
  isSystemBack,
  openPanel,
  panelClick,
  panelKey,
  remoteKey,
  selectNewFlag,
  verdictForDigit,
  wheelKey,
  type PanelEffect,
  type PointerAction,
  type RatingPanelState,
} from './rating/rating-panel'
import type { NormalizedRect, PublicQuote, QuoteQuality, QuoteRating, QuoteVisuals } from './types'

/** Show a layer even if its background hasn't loaded by then, rather than holding the old one. */
const READY_TIMEOUT_MS = 3000

/** After a rating is saved, how long its confirmation shows before the next quote. */
const ADVANCE_AFTER_RATING_MS = 1000
const TOAST_MS = 2500

/** How many quotes to ask for at a time; the server caps it. */
const BATCH_SIZE = 100

/** Wheel events closer together than this count as one notch. */
const WHEEL_STEP_MS = 150

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

function isPanelHistoryEntry(state: unknown): boolean {
  return typeof state === 'object' && state !== null && 'ratingPanel' in state
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
  // Rating mode (docs/features/quote-quality.md, "Human ratings on TV") is fetched with each quote, like the quality
  // overlay. It belongs to the quote it was fetched for; the last answer says whether the mode is on, so it does not
  // flicker off between quotes. While it is on, quotes only change when you rate them or step on.
  const [rating, setRating] = useState<{ quoteKey: string; rating: QuoteRating } | null>(null)
  const ratingMode = rating !== null
  const { currentIndex, currentQuote, next, previous } = useQuoteCarousel(quotes, !ratingMode)
  const stage = useStageSize()

  useEffect(() => {
    fetchRandomQuotes(BATCH_SIZE)
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
    fetchQuoteVisuals(currentQuote.id, currentQuote.excerptId)
      .then((visuals) =>
        setCompositions((previousCompositions) =>
          previousCompositions.map((entry, i) =>
            i === index ? { version: entry.version + 1, composition: compose(visuals) } : entry,
          ),
        ),
      )
      .catch((err) => console.error(err))
  }, [currentIndex, currentQuote])

  const currentQuoteKey = currentQuote ? `${currentQuote.id}:${currentQuote.excerptId}` : null

  // The quality overlay is fetched as each quote comes on screen, so switching it in the admin takes effect on the
  // next quote. It belongs to the quote it was fetched for, so a slow answer never labels another quote.
  const [quality, setQuality] = useState<{ quoteKey: string; quality: QuoteQuality } | null>(null)
  useEffect(() => {
    if (!currentQuote || !currentQuoteKey) return
    let cancelled = false
    fetchQuoteQuality(currentQuote.id, currentQuote.excerptId)
      .then((fetched) => {
        if (!cancelled) setQuality(fetched ? { quoteKey: currentQuoteKey, quality: fetched } : null)
      })
      .catch(() => {
        if (!cancelled) setQuality(null)
      })
    return () => {
      cancelled = true
    }
  }, [currentQuote, currentQuoteKey])
  const shownQuality = quality && quality.quoteKey === currentQuoteKey ? quality.quality : null

  useEffect(() => {
    if (!currentQuote || !currentQuoteKey) return
    let cancelled = false
    fetchQuoteRating(currentQuote.id, currentQuote.excerptId)
      .then((fetched) => {
        if (!cancelled) setRating(fetched ? { quoteKey: currentQuoteKey, rating: fetched } : null)
      })
      .catch(() => {
        if (!cancelled) setRating(null)
      })
    return () => {
      cancelled = true
    }
  }, [currentQuote, currentQuoteKey])
  const shownRating = rating && rating.quoteKey === currentQuoteKey ? rating.rating : null

  const [toast, setToast] = useState<ToastMessage | null>(null)
  const showToast = useCallback(
    (text: string, tone: ToastMessage['tone']) => setToast({ id: Date.now(), text, tone }),
    [],
  )
  useEffect(() => {
    if (!toast) return
    const timer = setTimeout(() => setToast(null), TOAST_MS)
    return () => clearTimeout(timer)
  }, [toast])

  // The rating panel, open on the quote it was opened for; stepping to another quote closes it.
  const [panel, setPanel] = useState<{ quoteKey: string; state: RatingPanelState } | null>(null)
  const shownPanel = panel && shownRating && panel.quoteKey === currentQuoteKey ? panel.state : null
  const [addingFlag, setAddingFlag] = useState(false)

  // Back on the remote is history.back() on webOS, and quits the app once there is no history left. So an open panel
  // has a history entry of its own: Back pops it and the panel closes on `popstate`, and closing it any other way pops
  // it too. While the new-flag field is open, Back only closes the field, and the entry is pushed again.
  const addingFlagRef = useRef(false)
  useEffect(() => {
    addingFlagRef.current = addingFlag
  }, [addingFlag])
  useEffect(() => {
    function handlePopState() {
      if (addingFlagRef.current) {
        setAddingFlag(false)
        window.history.pushState({ ratingPanel: true }, '')
      } else {
        setPanel(null)
      }
    }
    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [])
  const closePanel = useCallback(() => {
    setAddingFlag(false)
    if (isPanelHistoryEntry(window.history.state)) window.history.back()
    else setPanel(null)
  }, [])
  const [saving, setSaving] = useState(false)

  // After a rating, the next quote follows once the confirmation has been seen, unless you have stepped on already.
  const [advanceFrom, setAdvanceFrom] = useState<number | null>(null)
  useEffect(() => {
    if (advanceFrom !== currentIndex) return
    const timer = setTimeout(() => {
      setAdvanceFrom(null)
      next()
    }, ADVANCE_AFTER_RATING_MS)
    return () => clearTimeout(timer)
  }, [advanceFrom, currentIndex, next])

  const saveRating = useCallback(
    (verdict: string, problems: string[]) => {
      if (!currentQuote || !currentQuoteKey) return
      const index = currentIndex
      setSaving(true)
      saveQuoteRating(currentQuote.id, currentQuote.excerptId, verdict, problems)
        .then((saved) => {
          setRating({ quoteKey: currentQuoteKey, rating: saved })
          closePanel()
          showToast(`Rated: ${verdict}`, 'success')
          setAdvanceFrom(index)
        })
        .catch((err: Error) => showToast(`Couldn't save: ${err.message}`, 'error'))
        .finally(() => setSaving(false))
    },
    [currentQuote, currentQuoteKey, currentIndex, showToast, closePanel],
  )

  const addFlag = useCallback(
    (label: string) => {
      createQualityFlag(label)
        .then((flag) => {
          setRating((previousRating) => {
            if (!previousRating || previousRating.rating.flags.some((known) => known.code === flag.code)) {
              return previousRating
            }
            const flags = [...previousRating.rating.flags, flag]
            return { ...previousRating, rating: { ...previousRating.rating, flags } }
          })
          setPanel((previousPanel) => {
            if (!previousPanel || !shownRating) return previousPanel
            const codes = shownRating.flags.map((known) => known.code)
            if (!codes.includes(flag.code)) codes.push(flag.code)
            return { ...previousPanel, state: selectNewFlag(previousPanel.state, flag.code, codes) }
          })
          setAddingFlag(false)
        })
        .catch((err: Error) => showToast(`Couldn't add the flag: ${err.message}`, 'error'))
    },
    [shownRating, showToast],
  )
  const cancelNewFlag = useCallback(() => setAddingFlag(false), [])

  /** Applies a key, click or wheel step to the open panel. */
  const updatePanel = useCallback(
    (update: (state: RatingPanelState, flagCodes: string[]) => { state: RatingPanelState; effect?: PanelEffect }) => {
      if (!shownPanel || !shownRating || !currentQuoteKey || saving || addingFlag) return
      const result = update(shownPanel, shownRating.flags.map((flag) => flag.code))
      setPanel({ quoteKey: currentQuoteKey, state: result.state })
      if (result.effect?.kind === 'save') saveRating(result.effect.verdict, result.effect.problems)
      if (result.effect?.kind === 'cancel') closePanel()
      if (result.effect?.kind === 'newFlag') setAddingFlag(true)
    },
    [shownPanel, shownRating, currentQuoteKey, saving, addingFlag, saveRating, closePanel],
  )

  /** OK, or a click on the stage: opens the panel, or says why it can't. */
  const openRatingPanel = useCallback(() => {
    if (saving || !currentQuoteKey) return
    if (shownRating) {
      setPanel({ quoteKey: currentQuoteKey, state: openPanel(shownRating.verdict, shownRating.problems) })
      if (!isPanelHistoryEntry(window.history.state)) window.history.pushState({ ratingPanel: true }, '')
    } else if (shownQuality) {
      // The quality overlay shows this is an admin session; plain viewing ignores OK.
      showToast('Rating is off: turn on “Rate on TV” in the admin', 'error')
    }
  }, [saving, currentQuoteKey, shownRating, shownQuality, showToast])

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      // The new-flag field handles its own keys.
      if (event.target instanceof HTMLInputElement) return
      const key = remoteKey(event)
      if (shownPanel) {
        // The panel owns every key while it is open. The remote's Back arrives as `popstate`, so its key is not acted on
        // twice; Escape closes it here.
        event.preventDefault()
        if (isSystemBack(event)) return
        if (key) updatePanel((state, flagCodes) => panelKey(state, key, flagCodes))
        return
      }
      if (key === 'right' || key === 'left') {
        setAdvanceFrom(null)
        if (key === 'right') next()
        else previous()
      }
      if (key === 'up') reroll()
      if (key === 'ok') {
        event.preventDefault()
        openRatingPanel()
      } else if (key !== null && typeof key === 'object' && shownRating && !saving) {
        const verdict = verdictForDigit(key.digit)
        if (verdict) saveRating(verdict, shownRating.problems)
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [next, previous, reroll, shownPanel, shownRating, saving, saveRating, updatePanel, openRatingPanel])

  // The Magic Remote's wheel (or a mouse wheel) steps through the panel, one step per notch, however fine-grained
  // the events it sends.
  const lastWheelStep = useRef(0)
  const handleWheel = useCallback(
    (event: WheelEvent) => {
      const key = wheelKey(event.deltaY)
      if (!shownPanel || !key || event.timeStamp - lastWheelStep.current < WHEEL_STEP_MS) return
      lastWheelStep.current = event.timeStamp
      updatePanel((state, flagCodes) => panelKey(state, key, flagCodes))
    },
    [shownPanel, updatePanel],
  )

  const handlePanelPointer = useCallback(
    (action: PointerAction) => updatePanel((state, flagCodes) => panelClick(state, action, flagCodes)),
    [updatePanel],
  )

  // A click on the stage (the Magic Remote's wheel press while its pointer shows, or a mouse) works like OK; with the
  // panel open, a click beside it closes it like Back. Clicks on the panel itself stop there.
  const handleStageClick = useCallback(() => {
    if (addingFlag) return
    if (shownPanel) closePanel()
    else openRatingPanel()
  }, [addingFlag, shownPanel, openRatingPanel, closePanel])

  // In rating mode the batch is topped up before it runs out, with what is still unrated; when nothing new comes
  // back, the carousel simply wraps around.
  const refilling = useRef(false)
  const exhausted = useRef(false)
  useEffect(() => {
    if (!ratingMode || refilling.current || exhausted.current || quotes.length === 0) return
    if (currentIndex < quotes.length - 2) return
    refilling.current = true
    const known = new Set(quotes.map((quote) => `${quote.id}:${quote.excerptId}`))
    fetchRandomQuotes(BATCH_SIZE)
      .then((fetched) => {
        const fresh = fetched.filter((quote) => !known.has(`${quote.id}:${quote.excerptId}`))
        if (fresh.length === 0) {
          exhausted.current = true
          return
        }
        setQuotes((previousQuotes) => [...previousQuotes, ...fresh])
        setCompositions((previousCompositions) => [
          ...previousCompositions,
          ...fresh.map((quote) => ({ version: 0, composition: compose(quote.visuals) })),
        ])
      })
      .catch((err) => console.error(err))
      .finally(() => {
        refilling.current = false
      })
  }, [ratingMode, currentIndex, quotes])

  // Once the newest layer has faded in, the ones under it are hidden and can go.
  const dropCoveredLayers = useCallback(() => setLayers((previousLayers) => previousLayers.slice(-1)), [])
  const topLayer = layers[layers.length - 1]

  // Where each layer's quote was fitted, so the quality overlay can keep clear of the real text, not just its box.
  const [quoteRect, setQuoteRect] = useState<{ layerKey: string; rect: NormalizedRect } | null>(null)
  const reportQuoteRect = useCallback((layerKey: string, rect: NormalizedRect) => setQuoteRect({ layerKey, rect }), [])
  const topQuoteRect = topLayer && quoteRect?.layerKey === topLayer.key ? quoteRect.rect : null
  const shownQuoteRect = topLayer ? (topQuoteRect ?? topLayer.composition.quoteBox) : null
  // The panel takes the edge farther from the quote, so the quote stays readable while you rate it.
  const panelEdge = shownQuoteRect && shownQuoteRect.y + shownQuoteRect.height / 2 > 0.5 ? 'top' : 'bottom'

  return (
    <div className="flex h-full w-full items-center justify-center bg-black text-neutral-100">
      {status === 'loading' && <p className="text-xl text-neutral-400">Loading quotes…</p>}
      {status === 'error' && <p className="text-xl text-neutral-400">Couldn't load quotes.</p>}
      {status === 'ready' && (
        <div
          className="relative overflow-hidden"
          style={{ width: stage.width, height: stage.height }}
          onClick={handleStageClick}
          onWheel={handleWheel}
        >
          {layers.map((layer) => (
            <StageLayer
              key={layer.key}
              layer={layer}
              stageHeight={stage.height}
              onShown={dropCoveredLayers}
              onQuoteRect={reportQuoteRect}
            />
          ))}
          {(shownQuality || shownRating) && topLayer && shownQuoteRect && !shownPanel && (
            <QualityOverlay
              quality={shownQuality}
              rating={shownRating}
              quoteRect={shownQuoteRect}
              icons={topLayer.composition.icons}
              stageWidth={stage.width}
              stageHeight={stage.height}
            />
          )}
          {shownPanel && shownRating && (
            <RatingPanel
              state={shownPanel}
              flags={shownRating.flags}
              edge={panelEdge}
              addingFlag={addingFlag}
              saving={saving}
              stageHeight={stage.height}
              onPointer={handlePanelPointer}
              onNewFlag={addFlag}
              onCancelNewFlag={cancelNewFlag}
            />
          )}
          {toast && <Toast toast={toast} stageHeight={stage.height} />}
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
  onQuoteRect: (layerKey: string, rect: NormalizedRect) => void
}

/** A composition that stays invisible until its background is ready, then fades in over the previous one. */
function StageLayer({ layer, stageHeight, onShown, onQuoteRect }: StageLayerProps) {
  const [ready, setReady] = useState(false)
  const markReady = useCallback(() => setReady(true), [])
  const reportQuoteRect = useCallback((rect: NormalizedRect) => onQuoteRect(layer.key, rect), [onQuoteRect, layer.key])

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
      <QuoteStage quote={layer.quote} composition={layer.composition} stageHeight={stageHeight} onReady={markReady} onQuoteRect={reportQuoteRect} />
    </div>
  )
}

export default App
