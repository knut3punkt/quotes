import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'
import { useEffect, useMemo, useState } from 'react'
import { fetchTvDisplay, saveTvDisplay } from '../api'
import { toast } from '../hooks/use-toast'
import type { QualityRun, TvDisplaySettings } from '../types'
import { MultiSelectFilter } from './MultiSelectFilter'

/** The server's limit (`MAX_TV_QUALITY_RUNS`); more rows would not fit beside a quote. */
const MAX_RUNS = 5

interface TvQualityOverlayControlProps {
  qualityRuns: QualityRun[]
  /** The TV filter's run, the default when no run has been chosen yet. */
  tvFilterRunId: number | null
}

/**
 * Switches the TV's quality overlay (docs/features/quote-quality.md, "TV quality overlay"): while on, the TV shows
 * each shown quote's or excerpt's verdict and flags in each chosen run. Changes save at once and reach the TV with
 * its next quote.
 */
export function TvQualityOverlayControl({ qualityRuns, tvFilterRunId }: TvQualityOverlayControlProps) {
  const [settings, setSettings] = useState<TvDisplaySettings | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    fetchTvDisplay()
      .then(setSettings)
      .catch(() => undefined)
  }, [])

  const runOptions = useMemo(
    () => qualityRuns.map((run) => ({ value: String(run.id), label: run.label })),
    [qualityRuns],
  )
  const newestRunId = qualityRuns.length > 0 ? Math.max(...qualityRuns.map((run) => run.id)) : null
  const defaultRunId = tvFilterRunId ?? newestRunId
  const runIds = useMemo(() => {
    if (settings && settings.qualityRuns.length > 0) return new Set(settings.qualityRuns.map((run) => String(run.id)))
    return new Set(defaultRunId === null ? [] : [String(defaultRunId)])
  }, [settings, defaultRunId])

  async function save(showQuality: boolean, selected: Set<string>) {
    if (selected.size > MAX_RUNS) {
      toast.info(`The TV shows at most ${MAX_RUNS} runs`, 'Deselect one before adding another.')
      return
    }
    if (showQuality && selected.size === 0) {
      toast.info('Pick at least one run', 'Or untick “Show quality on TV”.')
      return
    }
    setSaving(true)
    try {
      setSettings(await saveTvDisplay({ showQuality, qualityRunIds: [...selected].map(Number) }))
    } catch (error) {
      toast.error('Could not change the TV quality overlay', error instanceof Error ? error.message : String(error))
    } finally {
      setSaving(false)
    }
  }

  const showQuality = settings?.showQuality ?? false
  return (
    <div className="flex flex-wrap items-center gap-2">
      <Checkbox
        id="tv-show-quality"
        checked={showQuality}
        disabled={settings === null || saving || runIds.size === 0}
        onCheckedChange={(checked) => save(checked === true, runIds)}
      />
      <Label htmlFor="tv-show-quality" className="font-normal">
        Show quality on TV
      </Label>
      <MultiSelectFilter
        id="tv-quality-runs"
        options={runOptions}
        selected={runIds}
        onChange={(selected) => save(showQuality, selected)}
        searchPlaceholder="Find a run…"
        emptyLabel="No runs"
        className="w-[260px]"
        disabled={settings === null || saving}
      />
      <span className="text-xs text-muted-foreground">up to {MAX_RUNS} runs, shown side by side</span>
    </div>
  )
}
