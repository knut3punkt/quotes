import { useLayoutEffect, useRef, useState } from 'react'

/** Largest and smallest quote font, as fractions of the box's stage height. */
const MAX_FONT_FRACTION = 0.072
const MIN_FONT_FRACTION = 0.026
const SEARCH_STEPS = 8

/**
 * Finds the largest font size, in px, at which the content fits its box, so a long quote shrinks
 * instead of spilling over the collage. [stageHeight] scales the bounds with the screen; the search
 * reruns when it or [contentKey] (anything that identifies the text and box) changes.
 */
export function useFitText<Box extends HTMLElement, Content extends HTMLElement>(
  stageHeight: number,
  contentKey: string,
) {
  const boxRef = useRef<Box>(null)
  const contentRef = useRef<Content>(null)
  const [fontSize, setFontSize] = useState(0)

  useLayoutEffect(() => {
    const box = boxRef.current
    const content = contentRef.current
    if (!box || !content || stageHeight <= 0) return
    const fits = (size: number) => {
      content.style.fontSize = `${size}px`
      return content.scrollHeight <= box.clientHeight && content.scrollWidth <= box.clientWidth
    }
    let low = stageHeight * MIN_FONT_FRACTION
    let high = stageHeight * MAX_FONT_FRACTION
    if (fits(high)) {
      low = high
    } else {
      for (let step = 0; step < SEARCH_STEPS; step++) {
        const middle = (low + high) / 2
        if (fits(middle)) low = middle
        else high = middle
      }
    }
    content.style.fontSize = `${low}px`
    setFontSize(low)
  }, [stageHeight, contentKey])

  return { boxRef, contentRef, fontSize }
}
