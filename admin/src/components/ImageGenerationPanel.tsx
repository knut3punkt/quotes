import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Progress } from '@/components/ui/progress'
import { ChevronDown, ChevronRight, ImagePlus, Loader2, Square } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { cancelImageGeneration, fetchImageGenerationStatus, startImageGeneration, subscribeImageGeneration } from '../api'
import { toast } from '../hooks/use-toast'
import { FACET_LABELS, facetChipClassName } from '../tagStyles'
import type { ImageGenerationItem, ImageGenerationItemStatus, ImageGenerationJobState } from '../types'

const ACTIVE_ITEM_STATUSES: ImageGenerationItemStatus[] = ['submitting', 'waiting', 'running', 'saving']
const FINISHED_ITEM_STATUSES: ImageGenerationItemStatus[] = ['done', 'failed', 'cancelled']

const ITEM_STATUS_LABELS: Record<ImageGenerationItemStatus, string> = {
  queued: 'Queued',
  submitting: 'Submitting',
  waiting: 'In ComfyUI queue',
  running: 'Generating',
  saving: 'Saving',
  done: 'Done',
  failed: 'Failed',
  cancelled: 'Cancelled',
}

const KIND_LABELS = { background: 'Background', element: 'Collage element' } as const

function itemStatusClassName(status: ImageGenerationItemStatus): string {
  switch (status) {
    case 'queued':
    case 'cancelled':
      return 'border-border bg-transparent text-muted-foreground'
    case 'submitting':
    case 'waiting':
    case 'running':
    case 'saving':
      return 'border-transparent bg-brand-tint text-primary'
    case 'done':
      return 'border-transparent bg-success/10 text-success'
    case 'failed':
      return 'border-transparent bg-destructive/10 text-destructive'
  }
}

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : 'Something went wrong'
}

function formatDuration(millis: number): string {
  const totalSeconds = Math.max(0, Math.round(millis / 1000))
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  if (hours > 0) return `${hours}h ${minutes}m`
  if (minutes > 0) return `${minutes}m ${String(seconds).padStart(2, '0')}s`
  return `${seconds}s`
}

/** Ticks once a second while `enabled`, so elapsed times stay live between server pushes. */
function useNow(enabled: boolean): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (!enabled) return
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [enabled])
  return now
}

/** Fraction of one item that is finished: whole for final items, sampler steps for the one generating. */
function itemCompletion(item: ImageGenerationItem): number {
  if (FINISHED_ITEM_STATUSES.includes(item.status)) return 1
  if (item.status === 'saving') return 0.98
  if (item.status === 'running' && item.step != null && item.maxSteps) return Math.min(item.step / item.maxSteps, 0.95)
  return 0
}

/**
 * Starts image generation for every mood and motif tag without an image, and follows the run live: the
 * server relays ComfyUI's WebSocket progress (queue position, sampler steps) for each tag, with the
 * prompt it was given.
 */
export function ImageGenerationPanel() {
  const [job, setJob] = useState<ImageGenerationJobState | null>(null)
  const [connected, setConnected] = useState(true)
  const [starting, setStarting] = useState(false)
  const [cancelling, setCancelling] = useState(false)
  const [expanded, setExpanded] = useState<Set<number>>(new Set())
  const [listOpen, setListOpen] = useState(true)
  const previousStatus = useRef<string | null>(null)
  const activeRowRef = useRef<HTMLLIElement | null>(null)

  useEffect(() => {
    fetchImageGenerationStatus()
      .then(setJob)
      .catch(() => setConnected(false))
    return subscribeImageGeneration(setJob, setConnected)
  }, [])

  // Announce the end of a run this page watched happen, not one that ended before it was opened.
  useEffect(() => {
    if (!job) return
    const previous = previousStatus.current
    previousStatus.current = job.status
    if (previous !== 'running' && previous !== 'cancelling') return
    const done = job.items.filter((item) => item.status === 'done').length
    const failed = job.items.filter((item) => item.status === 'failed').length
    if (job.status === 'finished') {
      if (failed > 0) toast.error('Image generation finished with failures', `${done} generated, ${failed} failed`)
      else toast.success('Image generation finished', `${done} images generated`)
    } else if (job.status === 'cancelled') {
      toast.info('Image generation cancelled', `${done} images generated before cancelling`)
    }
  }, [job])

  const running = job?.status === 'running' || job?.status === 'cancelling'
  const now = useNow(running)
  const activeIndex = job?.items.findIndex((item) => ACTIVE_ITEM_STATUSES.includes(item.status)) ?? -1

  useEffect(() => {
    activeRowRef.current?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  }, [activeIndex])

  const start = async () => {
    setStarting(true)
    try {
      const state = await startImageGeneration()
      setJob(state)
      setListOpen(true)
      if (state.items.length === 0) toast.info('Nothing to generate', 'Every used mood and motif tag already has an image.')
    } catch (err) {
      toast.error('Could not start image generation', errorMessage(err))
    } finally {
      setStarting(false)
    }
  }

  const cancel = async () => {
    setCancelling(true)
    try {
      setJob(await cancelImageGeneration())
    } catch (err) {
      toast.error('Could not cancel image generation', errorMessage(err))
    } finally {
      setCancelling(false)
    }
  }

  const toggleExpanded = (index: number) =>
    setExpanded((prev) => {
      const next = new Set(prev)
      if (next.has(index)) next.delete(index)
      else next.add(index)
      return next
    })

  const items = job?.items ?? []
  const counts = {
    done: items.filter((item) => item.status === 'done').length,
    failed: items.filter((item) => item.status === 'failed').length,
    cancelled: items.filter((item) => item.status === 'cancelled').length,
  }
  const remaining = items.length - counts.done - counts.failed - counts.cancelled
  const overall = items.length === 0 ? 0 : (items.reduce((sum, item) => sum + itemCompletion(item), 0) / items.length) * 100
  const elapsed = job?.startedAt ? (job.finishedAt ?? now) - job.startedAt : null

  return (
    <section className="mb-6 rounded-lg border border-border p-4" aria-labelledby="image-generation-heading">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 id="image-generation-heading" className="font-semibold">
            Tag images
          </h2>
          <p className="text-sm text-muted-foreground">
            Generates a background for each mood tag and a transparent collage element for each motif tag that is in
            use and has no image yet{job ? `, on ComfyUI at ${job.comfyUiBaseUrl}` : ''}.
          </p>
        </div>
        <div className="flex gap-2">
          {running && (
            <Button type="button" variant="outline" onClick={() => void cancel()} disabled={cancelling || job?.status === 'cancelling'}>
              {cancelling || job?.status === 'cancelling' ? <Loader2 className="animate-spin" /> : <Square />}
              {job?.status === 'cancelling' ? 'Cancelling…' : 'Cancel'}
            </Button>
          )}
          <Button type="button" onClick={() => void start()} disabled={starting || running}>
            {starting || running ? <Loader2 className="animate-spin" /> : <ImagePlus />}
            {running ? 'Generating…' : 'Generate missing images'}
          </Button>
        </div>
      </div>

      {!connected && (
        <Alert variant="destructive" className="mt-3 border-destructive/30 bg-destructive/10">
          <AlertDescription>Lost the live connection to the server; reconnecting…</AlertDescription>
        </Alert>
      )}
      {job?.connectionWarning && running && (
        <Alert className="mt-3 border-warning/30 bg-warning/10 text-warning">
          <AlertDescription>{job.connectionWarning}</AlertDescription>
        </Alert>
      )}

      {items.length > 0 && (
        <div className="mt-4">
          <div className="mb-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm">
            <button
              type="button"
              className="flex items-center gap-1 font-medium hover:underline"
              onClick={() => setListOpen((open) => !open)}
              aria-expanded={listOpen}
            >
              {listOpen ? <ChevronDown className="size-4" /> : <ChevronRight className="size-4" />}
              {running ? 'Current run' : job?.status === 'cancelled' ? 'Last run (cancelled)' : 'Last run'}
            </button>
            <span className="text-success">{counts.done} done</span>
            {counts.failed > 0 && <span className="text-destructive">{counts.failed} failed</span>}
            {counts.cancelled > 0 && <span className="text-muted-foreground">{counts.cancelled} cancelled</span>}
            {remaining > 0 && <span className="text-muted-foreground">{remaining} remaining</span>}
            <span className="text-muted-foreground">of {items.length}</span>
            {elapsed != null && <span className="text-muted-foreground tabular-nums">{formatDuration(elapsed)} elapsed</span>}
            {running && job?.comfyQueueRemaining != null && (
              <span className="text-muted-foreground">ComfyUI queue: {job.comfyQueueRemaining}</span>
            )}
          </div>
          <Progress value={overall} aria-label="Overall progress" />

          {listOpen && (
            <ol className="mt-3 max-h-[440px] divide-y divide-border overflow-y-auto rounded-md border border-border">
              {items.map((item, index) => {
                const active = ACTIVE_ITEM_STATUSES.includes(item.status)
                const isExpanded = expanded.has(index)
                const duration = item.startedAt ? (item.finishedAt ?? (active ? now : item.startedAt)) - item.startedAt : null
                return (
                  <li
                    key={`${item.tagId}-${index}`}
                    ref={index === activeIndex ? activeRowRef : undefined}
                    className={`px-3 py-2 text-sm ${active ? 'bg-brand-tint/40' : ''}`}
                  >
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="w-6 text-right text-xs text-muted-foreground tabular-nums">{index + 1}</span>
                      <span className={`rounded-full border px-2 py-0.5 text-xs ${facetChipClassName(item.facet)}`}>
                        {FACET_LABELS[item.facet]}
                      </span>
                      <span className="font-medium">{item.tagName}</span>
                      <span className="text-xs text-muted-foreground">{KIND_LABELS[item.kind]}</span>
                      <span className="ml-auto flex items-center gap-2">
                        {duration != null && (
                          <span className="text-xs text-muted-foreground tabular-nums">{formatDuration(duration)}</span>
                        )}
                        <Badge variant="outline" className={itemStatusClassName(item.status)}>
                          {active && <Loader2 className="animate-spin" />}
                          {ITEM_STATUS_LABELS[item.status]}
                          {item.status === 'running' && item.step != null && item.maxSteps ? ` ${item.step}/${item.maxSteps}` : ''}
                        </Badge>
                      </span>
                    </div>

                    {active && (
                      <Progress
                        className="mt-2"
                        value={item.status === 'running' && item.maxSteps ? ((item.step ?? 0) / item.maxSteps) * 100 : undefined}
                        aria-label={`Progress for ${item.tagName}`}
                      />
                    )}
                    {item.error && <p className="mt-1 pl-8 text-xs text-destructive">{item.error}</p>}
                    {item.prompt && (
                      <div className="mt-1 pl-8">
                        <button
                          type="button"
                          className="flex max-w-full items-start gap-1 text-left text-xs text-muted-foreground hover:text-foreground"
                          onClick={() => toggleExpanded(index)}
                          aria-expanded={isExpanded}
                        >
                          {isExpanded ? (
                            <ChevronDown className="mt-0.5 size-3 shrink-0" />
                          ) : (
                            <ChevronRight className="mt-0.5 size-3 shrink-0" />
                          )}
                          <span className={isExpanded ? 'whitespace-pre-wrap' : 'truncate'}>{item.prompt}</span>
                        </button>
                        {isExpanded && (
                          <p className="mt-1 pl-4 text-xs text-muted-foreground">
                            Seed {item.seed}
                            {item.filePath ? ` · saved as ${item.filePath}` : ''}
                          </p>
                        )}
                      </div>
                    )}
                  </li>
                )
              })}
            </ol>
          )}
        </div>
      )}
    </section>
  )
}
