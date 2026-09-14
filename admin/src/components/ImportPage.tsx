import { useState } from 'react'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { importWikiquoteAuthors } from '../api'
import type { SourceConfidence, WikiquoteAuthorImportResult } from '../types'
import { QuickImportsSection } from './QuickImportsSection'
import { WikiquoteAuthorPicker } from './WikiquoteAuthorPicker'

const CONFIDENCE_OPTIONS: SourceConfidence[] = ['sourced', 'attributed', 'unsourced', 'disputed']

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : 'Something went wrong'
}

function formatSections(sections: Record<string, number>): string {
  const entries = Object.entries(sections)
  if (entries.length === 0) return '—'
  return entries.map(([section, count]) => `${section}: ${count}`).join(', ')
}

export function ImportPage() {
  const [authorNames, setAuthorNames] = useState<string[]>([])
  const [selectedConfidence, setSelectedConfidence] = useState<Set<SourceConfidence>>(new Set(CONFIDENCE_OPTIONS))
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [results, setResults] = useState<WikiquoteAuthorImportResult[] | null>(null)

  const toggleConfidence = (option: SourceConfidence) => {
    setSelectedConfidence((prev) => {
      const next = new Set(prev)
      if (next.has(option)) next.delete(option)
      else next.add(option)
      return next
    })
  }

  const handleSubmit = async () => {
    if (authorNames.length === 0 || selectedConfidence.size === 0) return
    setSubmitting(true)
    setError(null)
    try {
      const response = await importWikiquoteAuthors({
        authorNames,
        sourceConfidence: Array.from(selectedConfidence),
      })
      setResults(response.results)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex flex-col gap-8">
      <div>
        <h2 className="mb-3 text-[15px] font-medium">From Wikiquote</h2>

        <div className="mb-5 flex flex-col gap-4 border-b border-border pb-5">
          <div className="flex flex-col gap-1">
            <Label htmlFor="author-search" className="text-xs font-normal text-muted-foreground">
              Authors
            </Label>
            <WikiquoteAuthorPicker id="author-search" value={authorNames} onChange={setAuthorNames} />
          </div>

          <div className="flex flex-col gap-1.5">
            <span className="text-xs font-normal text-muted-foreground">Source confidence</span>
            <div className="flex flex-wrap gap-2">
              {CONFIDENCE_OPTIONS.map((option) => {
                const active = selectedConfidence.has(option)
                return (
                  <button
                    key={option}
                    type="button"
                    aria-pressed={active}
                    onClick={() => toggleConfidence(option)}
                    className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-[13px] capitalize transition-colors ${
                      active ? 'border-primary bg-brand-tint text-primary' : 'border-border bg-card text-muted-foreground'
                    }`}
                  >
                    {option}
                  </button>
                )
              })}
            </div>
          </div>

          <div>
            <Button
              type="button"
              disabled={submitting || authorNames.length === 0 || selectedConfidence.size === 0}
              onClick={handleSubmit}
            >
              {submitting ? 'Importing…' : 'Import'}
            </Button>
          </div>
        </div>

        {error && (
          <Alert variant="destructive" className="mb-4 border-destructive/30 bg-destructive/10">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        {results && (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Requested name</TableHead>
                <TableHead>Resolved title</TableHead>
                <TableHead>Inserted</TableHead>
                <TableHead>Skipped as duplicate</TableHead>
                <TableHead>By section</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {results.map((result) => (
                <TableRow key={result.requestedName}>
                  <TableCell className="align-top">{result.requestedName}</TableCell>
                  <TableCell className="align-top">
                    {result.found ? (
                      result.resolvedTitle ?? '—'
                    ) : (
                      <Badge variant="outline" className="border-destructive/30 text-destructive">
                        not found
                      </Badge>
                    )}
                  </TableCell>
                  <TableCell className="align-top">{result.quotesInserted}</TableCell>
                  <TableCell className="align-top">{result.quotesSkippedAsDuplicate}</TableCell>
                  <TableCell className="align-top whitespace-normal text-muted-foreground">
                    {formatSections(result.quotesBySection)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </div>

      <QuickImportsSection />
    </div>
  )
}
