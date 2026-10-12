/** Verdict colours, after the admin's verdict badges: green for the best down to red for reject. */
export const VERDICT_COLORS: Record<string, string> = {
  excellent: '#15803d',
  good: '#4d7c0f',
  acceptable: '#a16207',
  weak: '#c2410c',
  reject: '#b91c1c',
}

export const MUTED_COLOR = '#525252'

export function verdictColor(verdict: string | null): string {
  return verdict ? (VERDICT_COLORS[verdict] ?? MUTED_COLOR) : MUTED_COLOR
}
