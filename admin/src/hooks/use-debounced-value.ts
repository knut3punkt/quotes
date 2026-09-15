import { useEffect, useState } from 'react'

export function useDebouncedValue<T>(value: T, delayMillis: number): T {
  const [debounced, setDebounced] = useState(value)

  useEffect(() => {
    const timeout = setTimeout(() => setDebounced(value), delayMillis)
    return () => clearTimeout(timeout)
  }, [value, delayMillis])

  return debounced
}
