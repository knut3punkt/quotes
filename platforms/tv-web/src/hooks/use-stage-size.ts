import { useEffect, useState } from 'react'

const STAGE_ASPECT = 16 / 9

interface StageSize {
  width: number
  height: number
}

function fitStage(): StageSize {
  const width = Math.min(window.innerWidth, window.innerHeight * STAGE_ASPECT)
  return { width, height: width / STAGE_ASPECT }
}

/**
 * The largest 16:9 stage that fits the window. Compositions are laid out on a 16:9 stage, so a window
 * of another shape gets letterboxed rather than stretched. Computed in script because older webOS
 * browsers lack CSS `min()`.
 */
export function useStageSize(): StageSize {
  const [size, setSize] = useState(fitStage)
  useEffect(() => {
    const update = () => setSize(fitStage())
    window.addEventListener('resize', update)
    return () => window.removeEventListener('resize', update)
  }, [])
  return size
}
