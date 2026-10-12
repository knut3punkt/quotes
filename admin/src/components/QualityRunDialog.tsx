import { useState } from 'react'
import { Loader2 } from 'lucide-react'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { createQualityRun } from '../api'
import type { QualityRun } from '../types'

const NEW_RUN = 'new'

interface QualityRunDialogProps {
  quoteCount: number
  /** Runs assessed by the server's model; external runs are filled by uploads, not from here. */
  llmRuns: QualityRun[]
  onCancel: () => void
  onStart: (run: QualityRun) => void
}

/** Picks the run a quality assessment goes into: an existing model run, or a new one. */
export function QualityRunDialog({ quoteCount, llmRuns, onCancel, onStart }: QualityRunDialogProps) {
  const [choice, setChoice] = useState(llmRuns[0] ? String(llmRuns[0].id) : NEW_RUN)
  const [label, setLabel] = useState('')
  const [notes, setNotes] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const start = async () => {
    if (choice !== NEW_RUN) {
      const run = llmRuns.find((candidate) => String(candidate.id) === choice)
      if (run) onStart(run)
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      onStart(await createQualityRun({ label: label.trim(), runner: 'llm', notes: notes.trim() || undefined }))
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause))
      setSubmitting(false)
    }
  }

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && !submitting) onCancel()
      }}
    >
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Assess quality of {quoteCount} quote{quoteCount === 1 ? '' : 's'}</DialogTitle>
        </DialogHeader>

        <p className="text-sm text-muted-foreground">
          Each quote and its accepted excerpts get a verdict from the server's model. Earlier runs are kept, so a new
          run can be compared with them. Re-assessing a quote in the same run replaces its verdict.
        </p>

        <div className="flex flex-col gap-2">
          <Label htmlFor="quality-run-choice">Run</Label>
          <Select value={choice} onValueChange={setChoice}>
            <SelectTrigger id="quality-run-choice">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {llmRuns.map((run) => (
                <SelectItem key={run.id} value={String(run.id)}>
                  #{run.id} {run.label} ({run.modelId})
                </SelectItem>
              ))}
              <SelectItem value={NEW_RUN}>New run…</SelectItem>
            </SelectContent>
          </Select>
        </div>

        {choice === NEW_RUN && (
          <>
            <div className="flex flex-col gap-2">
              <Label htmlFor="quality-run-label">Label</Label>
              <Input
                id="quality-run-label"
                value={label}
                placeholder="e.g. ministral full library"
                onChange={(event) => setLabel(event.target.value)}
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="quality-run-notes">Notes</Label>
              <Input id="quality-run-notes" value={notes} onChange={(event) => setNotes(event.target.value)} />
            </div>
          </>
        )}

        {error && (
          <Alert variant="destructive" className="border-destructive/30 bg-destructive/10">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        <DialogFooter>
          <Button type="button" variant="outline" onClick={onCancel} disabled={submitting}>
            Cancel
          </Button>
          <Button type="button" onClick={start} disabled={submitting || (choice === NEW_RUN && label.trim() === '')}>
            {submitting && <Loader2 className="animate-spin" />}
            Assess
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
