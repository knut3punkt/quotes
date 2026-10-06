function sameSet<T>(a: Set<T>, b: Set<T>): boolean {
  return a.size === b.size && [...a].every((value) => b.has(value))
}

/** Whether every control in a filter bar is at its default; Set-valued controls compare by contents. */
export function isDefaultFilterState<T extends object>(filters: T, defaults: T): boolean {
  return (Object.keys(defaults) as (keyof T)[]).every((key) => {
    const value = filters[key]
    const defaultValue = defaults[key]
    return value instanceof Set && defaultValue instanceof Set ? sameSet(value, defaultValue) : value === defaultValue
  })
}
