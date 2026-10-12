import { useEffect, useRef, type KeyboardEvent } from 'react'
import { VERDICTS, type PointerAction, type RatingPanelState } from '../rating/rating-panel'
import type { QualityFlag } from '../types'
import { verdictColor } from './verdict-colors'

interface RatingPanelProps {
  state: RatingPanelState
  flags: QualityFlag[]
  /** Which edge it sits on: the one away from the quote, so the quote stays readable while rating. */
  edge: 'top' | 'bottom'
  /** Whether the "+ New" chip has opened its text field. */
  addingFlag: boolean
  saving: boolean
  stageHeight: number
  /** A click on a verdict or chip: the Magic Remote's pointer, or a mouse. */
  onPointer: (action: PointerAction) => void
  onNewFlag: (label: string) => void
  onCancelNewFlag: () => void
}

/**
 * The rating panel (docs/features/quote-quality.md, "Human ratings on TV"): the verdict scale on top, flag chips
 * below. Keys are handled by the app through `panelKey`; this only draws the state, except for the new-flag field,
 * which takes typing (or, on webOS, the on-screen keyboard and the remote's voice button) itself.
 */
export function RatingPanel({
  state,
  flags,
  edge,
  addingFlag,
  saving,
  stageHeight,
  onPointer,
  onNewFlag,
  onCancelNewFlag,
}: RatingPanelProps) {
  const inputRef = useRef<HTMLInputElement>(null)
  useEffect(() => {
    if (addingFlag) inputRef.current?.focus()
  }, [addingFlag])

  function handleInputKey(event: KeyboardEvent<HTMLInputElement>) {
    // The field keeps its keys to itself; only Enter and Back mean something beyond typing.
    event.stopPropagation()
    if (event.key === 'Enter') {
      const label = event.currentTarget.value.trim()
      if (label) onNewFlag(label)
      else onCancelNewFlag()
    } else if (event.key === 'Escape') {
      // The remote's Back is history.back(), which the app turns into closing this field.
      event.preventDefault()
      onCancelNewFlag()
    }
  }

  const onVerdictRow = state.row === 'verdict' && !addingFlag
  const onFlagsRow = state.row === 'flags' && !addingFlag
  return (
    <div
      className="absolute inset-x-[8%] flex flex-col gap-[0.7em] rounded-[0.8em] bg-black/80 px-[1.2em] py-[0.9em] text-neutral-100 shadow-2xl"
      style={{ [edge]: '5%', fontSize: `${stageHeight * 0.024}px`, opacity: saving ? 0.6 : 1 }}
      // Clicks on the panel stay here; a click beside it reaches the stage, which closes the panel.
      onClick={(event) => event.stopPropagation()}
    >
      <div className="flex justify-center gap-[0.6em]">
        {VERDICTS.map((verdict, index) => {
          const chosen = index === state.verdictIndex
          return (
            <span
              key={verdict}
              onClick={() => onPointer({ kind: 'verdict', index })}
              className={`cursor-pointer rounded-full px-[0.9em] py-[0.25em] font-semibold uppercase tracking-wide transition-transform ${
                chosen && onVerdictRow ? 'scale-110 ring-[0.15em] ring-white' : ''
              }`}
              style={{ backgroundColor: chosen ? verdictColor(verdict) : 'rgba(64, 64, 64, 0.8)' }}
            >
              <span className="mr-[0.4em] text-neutral-300">{index + 1}</span>
              {verdict}
            </span>
          )
        })}
      </div>
      <div className="flex flex-wrap justify-center gap-[0.45em]" style={{ fontSize: '0.8em' }}>
        {flags.map((flag, index) => {
          const selected = state.selected.includes(flag.code)
          const focused = onFlagsRow && index === state.flagIndex
          return (
            <span
              key={flag.code}
              onClick={() => onPointer({ kind: 'flag', index })}
              className={`cursor-pointer rounded-full border px-[0.8em] py-[0.2em] ${
                selected ? 'border-amber-300 bg-amber-300 text-black' : 'border-neutral-500 text-neutral-200'
              } ${focused ? 'ring-[0.18em] ring-white' : ''}`}
            >
              {selected ? '✓ ' : ''}
              {flag.label}
            </span>
          )
        })}
        {addingFlag ? (
          <input
            ref={inputRef}
            className="rounded-full border border-white bg-neutral-900 px-[0.8em] py-[0.2em] text-neutral-50 outline-none"
            placeholder="New flag, then OK"
            maxLength={60}
            onKeyDown={handleInputKey}
          />
        ) : (
          <span
            onClick={() => onPointer({ kind: 'flag', index: flags.length })}
            className={`cursor-pointer rounded-full border border-dashed border-neutral-400 px-[0.8em] py-[0.2em] text-neutral-200 ${
              onFlagsRow && state.flagIndex >= flags.length ? 'ring-[0.18em] ring-white' : ''
            }`}
          >
            + New
          </span>
        )}
      </div>
      <p className="text-center text-neutral-400" style={{ fontSize: '0.7em' }}>
        {addingFlag
          ? 'Type or speak a flag · OK adds it · Back cancels'
          : '← → or wheel choose · ↓ flags · OK or click saves (on a flag: toggles) · 1–5 save · Back cancels'}
      </p>
    </div>
  )
}
