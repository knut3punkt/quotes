import type { CSSProperties } from 'react'
import type { NormalizedRect } from '../types'

/** Absolute-position style for a rectangle given in fractions of its container. */
export function percentRect(rect: NormalizedRect): CSSProperties {
  return {
    left: `${rect.x * 100}%`,
    top: `${rect.y * 100}%`,
    width: `${rect.width * 100}%`,
    height: `${rect.height * 100}%`,
  }
}
