/** The verdict scale, worst first, as the panel lists it left to right (docs/features/quote-quality.md). */
export const VERDICTS = ['reject', 'weak', 'acceptable', 'good', 'excellent'] as const
export type Verdict = (typeof VERDICTS)[number]

/** Preselected when the shown text has no rating yet: the middle of the scale. */
const DEFAULT_VERDICT_INDEX = VERDICTS.indexOf('acceptable')

/** webOS remotes send keyCode 461 for Back, which browsers report under a few different `key` names. */
const WEBOS_BACK_KEY_CODE = 461

/** What a key means to the rating UI, whatever remote or keyboard sent it. */
export type RemoteKey = 'left' | 'right' | 'up' | 'down' | 'ok' | 'back' | { digit: number }

export function remoteKey(event: Pick<KeyboardEvent, 'key' | 'keyCode'>): RemoteKey | null {
  switch (event.key) {
    case 'ArrowLeft':
      return 'left'
    case 'ArrowRight':
      return 'right'
    case 'ArrowUp':
      return 'up'
    case 'ArrowDown':
      return 'down'
    case 'Enter':
      return 'ok'
    case 'Escape':
    case 'Backspace':
    case 'GoBack':
    case 'BrowserBack':
      return 'back'
  }
  if (/^[0-9]$/.test(event.key)) return { digit: Number(event.key) }
  // Older webOS browsers can report `Unidentified` for remote keys, so fall back to the key code.
  return KEY_CODES[event.keyCode] ?? null
}

const KEY_CODES: Record<number, RemoteKey> = {
  13: 'ok',
  37: 'left',
  38: 'up',
  39: 'right',
  40: 'down',
  [WEBOS_BACK_KEY_CODE]: 'back',
}

/**
 * The remote's Back button (or a browser's), as opposed to Escape. webOS turns it into `history.back()` itself, and
 * offers to quit the app when there is no history left, so a layer that Back should close (the rating panel) pushes a
 * history entry and closes on `popstate` instead of on this key.
 */
export function isSystemBack(event: Pick<KeyboardEvent, 'key' | 'keyCode'>): boolean {
  return event.keyCode === WEBOS_BACK_KEY_CODE || event.key === 'GoBack' || event.key === 'BrowserBack'
}

/** The pointer: a mouse, or the LG Magic Remote's pointer, whose wheel press is a click rather than an OK key. */
export type PointerAction = { kind: 'verdict'; index: number } | { kind: 'flag'; index: number }

/**
 * What a click on a chip does: the same as moving there and pressing OK, so clicking a verdict saves it and clicking a
 * flag toggles it (or, on "+ New", asks for a new flag).
 */
export function panelClick(
  state: RatingPanelState,
  action: PointerAction,
  flagCodes: string[],
): { state: RatingPanelState; effect?: PanelEffect } {
  const moved: RatingPanelState =
    action.kind === 'verdict'
      ? { ...state, row: 'verdict', verdictIndex: action.index }
      : { ...state, row: 'flags', flagIndex: action.index }
  return panelKey(moved, 'ok', flagCodes)
}

/** The wheel steps along the verdicts: down (away from you) to a better one, like ▶. */
export function wheelKey(deltaY: number): RemoteKey | null {
  if (deltaY > 0) return 'right'
  if (deltaY < 0) return 'left'
  return null
}

/** The verdict a number key gives, 1 = reject ... 5 = excellent, or null for any other digit. */
export function verdictForDigit(digit: number): Verdict | null {
  return digit >= 1 && digit <= VERDICTS.length ? VERDICTS[digit - 1] : null
}

/**
 * The rating panel: a row of verdicts and, below it, a row of flag chips ending in "+ New". [flagIndex] equal to
 * the number of flags is the "+ New" chip.
 */
export interface RatingPanelState {
  row: 'verdict' | 'flags'
  verdictIndex: number
  flagIndex: number
  /** Toggled flag codes, in the order they were picked. */
  selected: string[]
}

export type PanelEffect =
  { kind: 'save'; verdict: Verdict; problems: string[] } | { kind: 'cancel' } | { kind: 'newFlag' }

export function openPanel(verdict: string | null, problems: string[]): RatingPanelState {
  const index = VERDICTS.indexOf(verdict as Verdict)
  return { row: 'verdict', verdictIndex: index >= 0 ? index : DEFAULT_VERDICT_INDEX, flagIndex: 0, selected: problems }
}

/** Adds a just-created flag to the selection and puts focus on it. */
export function selectNewFlag(state: RatingPanelState, code: string, flagCodes: string[]): RatingPanelState {
  const index = flagCodes.indexOf(code)
  return {
    ...state,
    row: 'flags',
    flagIndex: index >= 0 ? index : state.flagIndex,
    selected: state.selected.includes(code) ? state.selected : [...state.selected, code],
  }
}

function clamp(value: number, max: number): number {
  return Math.min(Math.max(value, 0), max)
}

/**
 * What [key] does to the panel, given the flag chips on offer ([flagCodes], in display order). OK on the verdict row
 * and a number key both save; OK on a chip toggles it, or on "+ New" asks for a new flag; Back cancels.
 */
export function panelKey(
  state: RatingPanelState,
  key: RemoteKey,
  flagCodes: string[],
): { state: RatingPanelState; effect?: PanelEffect } {
  const save = (verdictIndex: number): PanelEffect => ({
    kind: 'save',
    verdict: VERDICTS[verdictIndex],
    // Kept in the order the chips are shown, whatever order they were picked in.
    problems: flagCodes.filter((code) => state.selected.includes(code)),
  })
  if (typeof key === 'object') {
    const verdict = verdictForDigit(key.digit)
    return verdict ? { state, effect: save(VERDICTS.indexOf(verdict)) } : { state }
  }
  if (key === 'back') return { state, effect: { kind: 'cancel' } }

  if (state.row === 'verdict') {
    switch (key) {
      case 'left':
        return { state: { ...state, verdictIndex: clamp(state.verdictIndex - 1, VERDICTS.length - 1) } }
      case 'right':
        return { state: { ...state, verdictIndex: clamp(state.verdictIndex + 1, VERDICTS.length - 1) } }
      case 'down':
        return { state: { ...state, row: 'flags' } }
      case 'ok':
        return { state, effect: save(state.verdictIndex) }
      default:
        return { state }
    }
  }

  // The flags row: every flag, then "+ New" at index flagCodes.length.
  switch (key) {
    case 'left':
      return { state: { ...state, flagIndex: clamp(state.flagIndex - 1, flagCodes.length) } }
    case 'right':
      return { state: { ...state, flagIndex: clamp(state.flagIndex + 1, flagCodes.length) } }
    case 'up':
      return { state: { ...state, row: 'verdict' } }
    case 'ok': {
      if (state.flagIndex >= flagCodes.length) return { state, effect: { kind: 'newFlag' } }
      const code = flagCodes[state.flagIndex]
      const selected = state.selected.includes(code)
        ? state.selected.filter((picked) => picked !== code)
        : [...state.selected, code]
      return { state: { ...state, selected } }
    }
    default:
      return { state }
  }
}
