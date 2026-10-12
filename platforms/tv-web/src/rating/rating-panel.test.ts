import { describe, expect, it } from 'vitest'
import {
  isSystemBack,
  openPanel,
  panelClick,
  panelKey,
  remoteKey,
  selectNewFlag,
  verdictForDigit,
  wheelKey,
  type RatingPanelState,
  type RemoteKey,
} from './rating-panel'

const FLAGS = ['FRAGMENT', 'FLAT', 'TOO_PREACHY']

function press(state: RatingPanelState, ...keys: RemoteKey[]) {
  let current = state
  for (const key of keys) {
    const result = panelKey(current, key, FLAGS)
    if (result.effect) return { state: result.state, effect: result.effect }
    current = result.state
  }
  return { state: current, effect: undefined }
}

describe('remoteKey', () => {
  it('reads arrows, OK, number keys and every form of Back', () => {
    expect(remoteKey({ key: 'ArrowDown', keyCode: 40 })).toBe('down')
    expect(remoteKey({ key: 'Enter', keyCode: 13 })).toBe('ok')
    expect(remoteKey({ key: '4', keyCode: 52 })).toEqual({ digit: 4 })
    expect(remoteKey({ key: 'Unidentified', keyCode: 461 })).toBe('back')
    expect(remoteKey({ key: 'GoBack', keyCode: 0 })).toBe('back')
    expect(remoteKey({ key: 'Escape', keyCode: 27 })).toBe('back')
    expect(remoteKey({ key: 'a', keyCode: 65 })).toBeNull()
  })
})

describe('verdictForDigit', () => {
  it('maps 1-5 from reject to excellent', () => {
    expect(verdictForDigit(1)).toBe('reject')
    expect(verdictForDigit(5)).toBe('excellent')
    expect(verdictForDigit(0)).toBeNull()
    expect(verdictForDigit(6)).toBeNull()
  })
})

describe('panelKey', () => {
  it('opens on the existing rating, or on acceptable', () => {
    expect(openPanel('good', ['FLAT']).verdictIndex).toBe(3)
    expect(openPanel(null, []).verdictIndex).toBe(2)
  })

  it('moves along the verdicts, stopping at the ends, and saves with OK', () => {
    const { effect } = press(openPanel(null, []), 'right', 'right', 'right', 'ok')
    expect(effect).toEqual({ kind: 'save', verdict: 'excellent', problems: [] })
    expect(press(openPanel(null, []), 'left', 'left', 'left', 'ok').effect).toEqual({ kind: 'save', verdict: 'reject', problems: [] })
  })

  it('toggles flags on the second row and saves them in display order', () => {
    const { effect } = press(openPanel('weak', ['FLAT']), 'down', 'right', 'right', 'ok', 'left', 'ok', 'left', 'ok', 'up', 'ok')
    // FLAT was toggled off again; TOO_PREACHY and FRAGMENT were toggled on.
    expect(effect).toEqual({ kind: 'save', verdict: 'weak', problems: ['FRAGMENT', 'TOO_PREACHY'] })
  })

  it('asks for a new flag on the last chip, and selects it once created', () => {
    const { state, effect } = press(openPanel(null, []), 'down', 'right', 'right', 'right', 'right', 'ok')
    expect(effect).toEqual({ kind: 'newFlag' })
    const withNew = selectNewFlag(state, 'TOO_PREACHY', FLAGS)
    expect(withNew.selected).toEqual(['TOO_PREACHY'])
    expect(withNew.flagIndex).toBe(2)
  })

  it('saves straight away with a number key and cancels with Back', () => {
    expect(press(openPanel(null, ['FLAT']), { digit: 2 }).effect).toEqual({ kind: 'save', verdict: 'weak', problems: ['FLAT'] })
    expect(press(openPanel(null, []), 'down', 'back').effect).toEqual({ kind: 'cancel' })
    expect(press(openPanel(null, []), { digit: 9 }).effect).toBeUndefined()
  })
})

describe('pointer and wheel', () => {
  it('reads remote keys by key code when the browser reports them as unidentified', () => {
    expect(remoteKey({ key: 'Unidentified', keyCode: 13 })).toBe('ok')
    expect(remoteKey({ key: 'Unidentified', keyCode: 39 })).toBe('right')
  })

  it('saves a clicked verdict and toggles a clicked flag', () => {
    const panel = openPanel(null, [])
    expect(panelClick(panel, { kind: 'verdict', index: 4 }, FLAGS).effect).toEqual({
      kind: 'save',
      verdict: 'excellent',
      problems: [],
    })
    expect(panelClick(panel, { kind: 'flag', index: 1 }, FLAGS).state.selected).toEqual(['FLAT'])
    expect(panelClick(panel, { kind: 'flag', index: FLAGS.length }, FLAGS).effect).toEqual({ kind: 'newFlag' })
  })

  it('steps one verdict per wheel notch', () => {
    expect(wheelKey(120)).toBe('right')
    expect(wheelKey(-3)).toBe('left')
    expect(wheelKey(0)).toBeNull()
  })
})

describe('isSystemBack', () => {
  it('tells the remote Back button, which webOS turns into history.back(), from Escape', () => {
    expect(isSystemBack({ key: 'Unidentified', keyCode: 461 })).toBe(true)
    expect(isSystemBack({ key: 'GoBack', keyCode: 0 })).toBe(true)
    expect(isSystemBack({ key: 'Escape', keyCode: 27 })).toBe(false)
  })
})
