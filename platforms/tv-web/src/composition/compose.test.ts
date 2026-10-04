import { describe, expect, it } from 'vitest'
import { composeQuote, compositionImageUrls } from './compose'
import { TARGET_CONTRAST } from './contrast'
import { mulberry32 } from './random'
import { background, element, fieldFrom } from './test-fixtures'

const imageUrl = (id: number, width: number) => `img/${id}?w=${width}`

describe('composeQuote', () => {
  it('uses the chosen background image and requests every element', () => {
    const field = fieldFrom(() => ({ luminance: 0.1, detail: 0.01 }))
    const composition = composeQuote(
      { background: background(field), elements: [element(7), element(8)] },
      imageUrl,
      mulberry32(1),
    )

    expect(composition.background).toMatchObject({ kind: 'image', url: 'img/1?w=1920' })
    expect(compositionImageUrls(composition)).toEqual(['img/1?w=1920', ...composition.elements.map((e) => e.url)])
    expect(composition.elements.map((e) => e.imageId).sort()).toEqual([7, 8])
    expect(composition.debug.mood).toBe('serene')
  })

  it('falls back to a dark gradient with readable light text when there is no mood image', () => {
    const composition = composeQuote({ background: null, elements: [element(3)] }, imageUrl, mulberry32(9))

    expect(composition.background.kind).toBe('gradient')
    expect(composition.text.tone).toBe('light')
    expect(composition.text.contrast).toBeGreaterThanOrEqual(TARGET_CONTRAST)
    expect(compositionImageUrls(composition)).toEqual(['img/3?w=640'])
  })

  it('is reproducible for a seed', () => {
    const visuals = { background: null, elements: [element(1), element(2), element(3)] }
    expect(composeQuote(visuals, imageUrl, mulberry32(42))).toEqual(composeQuote(visuals, imageUrl, mulberry32(42)))
  })
})
