import { describe, expect, it } from 'vitest'
import { composeQuote, compositionImageUrls } from './compose'
import { TARGET_CONTRAST } from './contrast'
import { mulberry32 } from './random'
import { background, element, fieldFrom } from './test-fixtures'

const imageUrl = (id: number, width: number) => `img/${id}?w=${width}`

describe('composeQuote', () => {
  it('uses the chosen background image and requests the motif', () => {
    const field = fieldFrom(() => ({ luminance: 0.1, detail: 0.01 }))
    const composition = composeQuote(
      { background: background(field, '#335577', 'right'), elements: [element(7)], styleFit: 2 },
      imageUrl,
      mulberry32(1),
    )

    expect(composition.background).toMatchObject({ kind: 'image', url: 'img/1?w=1920' })
    expect(compositionImageUrls(composition)).toEqual(['img/1?w=1920', 'img/7?w=640'])
    expect(composition.debug).toMatchObject({
      mood: 'serene',
      moodStyle: 'neon-noir',
      motif: 'motif 7',
      motifStyle: 'cyberpunk',
      styleFit: 2,
      quoteLayout: 'right',
      arrangement: 'right',
    })
  })

  it('gives an overlay background a backdrop behind the quote, even when the text needs none', () => {
    const calmDark = fieldFrom(() => ({ luminance: 0.03, detail: 0.005 }))
    const compose = (quoteLayout: string) =>
      composeQuote({ background: background(calmDark, '#335577', quoteLayout), elements: [], styleFit: null }, imageUrl, mulberry32(1))

    expect(compose('overlay').text.backdrop?.reason).toBe('overlay')
    expect(compose('center').text.backdrop).toBeNull()
  })

  it('treats an unknown quote layout as none', () => {
    const field = fieldFrom(() => ({ luminance: 0.1, detail: 0.01 }))
    const composition = composeQuote(
      { background: background(field, '#335577', 'diagonal'), elements: [element(7)], styleFit: 0 },
      imageUrl,
      mulberry32(1),
    )
    expect(composition.debug.arrangement).toBe('grid')
  })

  it('falls back to a dark gradient with readable light text when there is no mood image', () => {
    const composition = composeQuote({ background: null, elements: [element(3)], styleFit: null }, imageUrl, mulberry32(9))

    expect(composition.background.kind).toBe('gradient')
    expect(composition.text.tone).toBe('light')
    expect(composition.text.contrast).toBeGreaterThanOrEqual(TARGET_CONTRAST)
    expect(compositionImageUrls(composition)).toEqual(['img/3?w=640'])
  })

  it('is reproducible for a seed', () => {
    const visuals = { background: null, elements: [element(1)], styleFit: null }
    expect(composeQuote(visuals, imageUrl, mulberry32(42))).toEqual(composeQuote(visuals, imageUrl, mulberry32(42)))
  })
})
