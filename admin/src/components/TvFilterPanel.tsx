import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Loader2, Tv } from 'lucide-react'
import { useEffect, useState } from 'react'
import { clearTvFilter, fetchTvFilter, saveTvFilter } from '../api'
import { toast } from '../hooks/use-toast'
import { quoteFilterStateFromQuery, type QuoteFilterState } from '../quoteFilters'
import type { FilterOption, QualityRun, QuoteFilterOptions, TvFilter } from '../types'
import { ConfirmDialog } from './ConfirmDialog'
import { TvQualityOverlayControl } from './TvQualityOverlayControl'

interface TvFilterPanelProps {
  /** The page's current filters, as the query `saveTvFilter` stores. */
  currentQuery: string
  options: QuoteFilterOptions
  qualityRuns: QualityRun[]
  onLoad: (filters: QuoteFilterState) => void
}

const ENRICHMENT_LABELS: Record<string, string> = { has: 'has', none: 'none found', notRun: 'not run' }

/** Equal filters can be encoded differently; compare them by their parameters. */
function canonicalQuery(query: string): string {
  return [...new URLSearchParams(query).entries()]
    .map(([name, value]) => `${name}=${value}`)
    .sort()
    .join('&')
}

function labelsOf(values: Set<string>, options: FilterOption[]): string {
  return [...values].map((value) => options.find((option) => option.value === value)?.label ?? value).join(', ')
}

/** A short, readable list of what a stored filter asks for. */
function describe(filter: TvFilter, options: QuoteFilterOptions): string[] {
  const state = quoteFilterStateFromQuery(filter.query)
  const parts: string[] = []
  if (state.qualityRun) parts.push(`run: ${filter.qualityRunLabel ?? `#${state.qualityRun}`}`)
  if (state.qualityVerdicts.size > 0) parts.push(`verdict: ${[...state.qualityVerdicts].join(', ')}`)
  if (state.qualityFlags.size > 0) {
    parts.push(`flag: ${[...state.qualityFlags].map((flag) => flag.toLowerCase().replaceAll('_', ' ')).join(', ')}`)
  }
  if (state.search) parts.push(`search: “${state.search}”`)
  if (state.authors.size > 0) parts.push(`author: ${labelsOf(state.authors, options.authors)}`)
  if (state.sources.size > 0) parts.push(`source: ${labelsOf(state.sources, options.sources)}`)
  if (state.languages.size > 0) parts.push(`language: ${labelsOf(state.languages, options.languages)}`)
  if (state.providers.size > 0) parts.push(`provider: ${labelsOf(state.providers, options.providers)}`)
  if (state.confidences.size > 0) parts.push(`confidence: ${labelsOf(state.confidences, options.sourceConfidences)}`)
  if (state.lengthValue) parts.push(`length ${state.lengthOp === 'above' ? '>' : '<'} ${state.lengthValue}`)
  if (state.tag) parts.push(`tag: ${state.tag}`)
  for (const [name, values] of [
    ['excerpts', state.excerpts],
    ['interpretations', state.interpretations],
    ['tags', state.tags],
  ] as const) {
    if (values.size > 0) parts.push(`${name}: ${[...values].map((value) => ENRICHMENT_LABELS[value]).join(', ')}`)
  }
  return parts
}

function poolSummary(filter: TvFilter): string {
  const quotes = `${filter.poolQuotes} quote${filter.poolQuotes === 1 ? '' : 's'}`
  return `${quotes}, ${filter.poolAsExcerpt} as an excerpt`
}

/**
 * The TV preview filter (docs/features/quote-quality.md, "TV preview filter"): shows what the TV is limited to,
 * if anything, and sends the page's current filters there. Quality verdicts apply to the text the TV shows.
 */
export function TvFilterPanel({ currentQuery, options, qualityRuns, onLoad }: TvFilterPanelProps) {
  const [tvFilter, setTvFilter] = useState<TvFilter | null>(null)
  const [saving, setSaving] = useState(false)
  const [confirmingClear, setConfirmingClear] = useState(false)
  const [clearing, setClearing] = useState(false)
  const [clearError, setClearError] = useState<string | null>(null)

  useEffect(() => {
    fetchTvFilter()
      .then(setTvFilter)
      .catch(() => undefined)
  }, [])

  const tvFilterRunId = tvFilter ? quoteFilterStateFromQuery(tvFilter.query).qualityRun : ''
  const matchesCurrent =tvFilter !== null && canonicalQuery(tvFilter.query) === canonicalQuery(currentQuery)

  async function showOnTv() {
    setSaving(true)
    try {
      const saved = await saveTvFilter(currentQuery)
      setTvFilter(saved)
      if (saved.poolQuotes === 0) toast.error('The TV filter matches nothing', 'The TV will show no quotes until it changes.')
      else toast.success('TV filter set', `The TV now shows ${poolSummary(saved)}.`)
    } catch (error) {
      toast.error('Could not set the TV filter', error instanceof Error ? error.message : String(error))
    } finally {
      setSaving(false)
    }
  }

  async function clear() {
    setClearing(true)
    setClearError(null)
    try {
      await clearTvFilter()
      setTvFilter(null)
      setConfirmingClear(false)
      toast.success('TV filter cleared', 'The TV shows every quote again.')
    } catch (error) {
      setClearError(error instanceof Error ? error.message : String(error))
    } finally {
      setClearing(false)
    }
  }

  return (
    <div className="mb-4 flex flex-wrap items-center gap-2 rounded-md border border-border px-3 py-2 text-sm">
      <Tv className="size-4 shrink-0 text-muted-foreground" aria-hidden />
      {tvFilter ? (
        <>
          <span>
            <span className="font-medium">TV limited to</span> {poolSummary(tvFilter)}:
          </span>
          {describe(tvFilter, options).map((part) => (
            <Badge key={part} variant="secondary" className="font-normal">
              {part}
            </Badge>
          ))}
          {matchesCurrent && <span className="text-muted-foreground">(the current filters)</span>}
          {!matchesCurrent && (
            <Button variant="outline" size="sm" onClick={() => onLoad(quoteFilterStateFromQuery(tvFilter.query))}>
              Load into filters
            </Button>
          )}
          <Button variant="ghost" size="sm" onClick={() => setConfirmingClear(true)}>
            Clear
          </Button>
        </>
      ) : (
        <span className="text-muted-foreground">The TV shows every quote.</span>
      )}
      <Button size="sm" className="ml-auto" onClick={showOnTv} disabled={saving || matchesCurrent}>
        {saving && <Loader2 className="animate-spin" />}
        Show these on TV
      </Button>

      <div className="basis-full border-t border-border pt-2">
        <TvQualityOverlayControl
          qualityRuns={qualityRuns}
          tvFilterRunId={tvFilterRunId ? Number(tvFilterRunId) : null}
        />
      </div>

      {confirmingClear && (
        <ConfirmDialog
          title="Clear the TV filter?"
          description="The TV goes back to showing every quote. Nothing else changes."
          confirmLabel="Clear"
          submittingLabel="Clearing…"
          submitting={clearing}
          error={clearError}
          onCancel={() => setConfirmingClear(false)}
          onConfirm={clear}
        />
      )}
    </div>
  )
}
