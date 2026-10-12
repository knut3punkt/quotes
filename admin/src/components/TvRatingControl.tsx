import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Loader2, Plus } from 'lucide-react'
import { useEffect, useState } from 'react'
import { createQualityRun, fetchTvDisplay, saveTvRatingRun } from '../api'
import { toast } from '../hooks/use-toast'
import type { QualityRun, TvDisplaySettings } from '../types'

interface TvRatingControlProps {
  qualityRuns: QualityRun[]
  onRunCreated: (run: QualityRun) => void
}

function defaultRunLabel(): string {
  return `Human ratings ${new Date().toISOString().slice(0, 10)}`
}

/**
 * Switches the TV's rating mode (docs/features/quote-quality.md, "Human ratings on TV"): while on, OK on the remote
 * opens a rating panel and the ratings go into the chosen human run, which compares with any other run on the
 * Quality page. Changes save at once and reach the TV with its next quote.
 */
export function TvRatingControl({ qualityRuns, onRunCreated }: TvRatingControlProps) {
  const [settings, setSettings] = useState<TvDisplaySettings | null>(null)
  const [chosenRunId, setChosenRunId] = useState('')
  const [saving, setSaving] = useState(false)
  /** The new run's label while one is being named, else null. */
  const [newLabel, setNewLabel] = useState<string | null>(null)

  useEffect(() => {
    fetchTvDisplay()
      .then(setSettings)
      .catch(() => undefined)
  }, [])

  // Runs come newest first, so the default is the latest human run.
  const humanRuns = qualityRuns.filter((run) => run.runner === 'human')
  const ratingOn = settings?.ratingRun != null
  const runId = settings?.ratingRun ? String(settings.ratingRun.id) : chosenRunId || String(humanRuns[0]?.id ?? '')

  async function save(on: boolean, id: string) {
    setSaving(true)
    try {
      // Turning it on before any human run exists starts one, rather than leaving the switch dead.
      let runToUse = id
      if (on && !runToUse) {
        const run = await createQualityRun({ label: defaultRunLabel(), runner: 'human' })
        onRunCreated(run)
        runToUse = String(run.id)
        setChosenRunId(runToUse)
      }
      setSettings(await saveTvRatingRun(on ? Number(runToUse) : null))
    } catch (error) {
      toast.error('Could not change rating on TV', error instanceof Error ? error.message : String(error))
    } finally {
      setSaving(false)
    }
  }

  function chooseRun(id: string) {
    setChosenRunId(id)
    if (ratingOn) void save(true, id)
  }

  async function createRun() {
    const label = newLabel?.trim()
    if (!label) return
    setSaving(true)
    try {
      const run = await createQualityRun({ label, runner: 'human' })
      onRunCreated(run)
      setNewLabel(null)
      setChosenRunId(String(run.id))
      if (ratingOn) setSettings(await saveTvRatingRun(run.id))
    } catch (error) {
      toast.error('Could not create the human run', error instanceof Error ? error.message : String(error))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="flex flex-wrap items-center gap-2">
      <Checkbox
        id="tv-rating"
        checked={ratingOn}
        disabled={settings === null || saving}
        onCheckedChange={(checked) => save(checked === true, runId)}
      />
      <Label htmlFor="tv-rating" className="font-normal">
        Rate on TV into
      </Label>
      {newLabel === null ? (
        <>
          <Select value={runId} onValueChange={chooseRun} disabled={saving || humanRuns.length === 0}>
            <SelectTrigger className="w-[260px]">
              <SelectValue placeholder="No human runs yet" />
            </SelectTrigger>
            <SelectContent>
              {humanRuns.map((run) => (
                <SelectItem key={run.id} value={String(run.id)}>
                  {run.label} ({run.assessedCount} rated)
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button variant="outline" size="sm" onClick={() => setNewLabel(defaultRunLabel())} disabled={saving}>
            <Plus />
            New human run
          </Button>
        </>
      ) : (
        <form
          className="flex items-center gap-2"
          onSubmit={(event) => {
            event.preventDefault()
            void createRun()
          }}
        >
          <Input
            autoFocus
            className="h-8 w-[260px]"
            value={newLabel}
            onChange={(event) => setNewLabel(event.target.value)}
            aria-label="New human run label"
          />
          <Button type="submit" size="sm" disabled={saving || !newLabel.trim()}>
            {saving && <Loader2 className="animate-spin" />}
            Create
          </Button>
          <Button type="button" variant="ghost" size="sm" onClick={() => setNewLabel(null)} disabled={saving}>
            Cancel
          </Button>
        </form>
      )}
      <span className="text-xs text-muted-foreground">OK on the remote rates · unrated quotes come first</span>
    </div>
  )
}
