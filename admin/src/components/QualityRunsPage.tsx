import { useCallback, useEffect, useState } from 'react'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { fetchQualityComparison, fetchQualityRuns } from '../api'
import { QUALITY_VERDICTS, type QualityAssessment, type QualityComparison, type QualityRun } from '../types'
import { VERDICT_CLASSES } from './QualityVerdictBadges'

function percent(share: number): string {
  return `${Math.round(share * 100)}%`
}

function runLabel(run: QualityRun): string {
  return `#${run.id} ${run.label}`
}

function AssessmentSummary({ assessment }: { assessment: QualityAssessment }) {
  return (
    <div className="flex flex-col gap-1 text-sm">
      {assessment.verdict && (
        <span className={`w-fit rounded px-1.5 py-0.5 text-xs ${VERDICT_CLASSES[assessment.verdict]}`}>{assessment.verdict}</span>
      )}
      <span>{assessment.assessment}</span>
      <span className="text-xs text-muted-foreground">
        {assessment.standsAlone}/{assessment.completeness}/{assessment.substance}/{assessment.expression}
        {assessment.problems.length > 0 && ` · ${assessment.problems.join(', ')}`}
      </span>
    </div>
  )
}

function ComparisonView({ comparison, runA, runB }: { comparison: QualityComparison; runA: QualityRun; runB: QualityRun }) {
  return (
    <div className="flex flex-col gap-6">
      <p className="text-sm text-muted-foreground">
        {comparison.matched} subjects assessed by both runs ({comparison.onlyInA} only in A, {comparison.onlyInB} only in
        B). Exact verdict agreement {percent(comparison.exactAgreement)}, within one step{' '}
        {percent(comparison.withinOneAgreement)}, weighted kappa {comparison.weightedKappa.toFixed(2)}. Keep (acceptable
        or better) versus drop: kappa {comparison.keepDropKappa.toFixed(2)}. Agreement is not accuracy: review the
        disagreements to see which run was right.
      </p>

      <div>
        <h2 className="mb-2 text-lg">Verdicts: rows {runLabel(runA)}, columns {runLabel(runB)}</h2>
        <Table className="w-auto">
          <TableHeader>
            <TableRow>
              <TableHead />
              {comparison.verdicts.map((verdict) => (
                <TableHead key={verdict}>{verdict}</TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {comparison.verdictMatrix.map((row, rowIndex) => (
              <TableRow key={comparison.verdicts[rowIndex]}>
                <TableHead>{comparison.verdicts[rowIndex]}</TableHead>
                {row.map((count, columnIndex) => (
                  <TableCell key={columnIndex} className={rowIndex === columnIndex ? 'font-semibold' : 'text-muted-foreground'}>
                    {count}
                  </TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      <div className="flex flex-wrap gap-10">
        <div>
          <h2 className="mb-2 text-lg">Levels</h2>
          <Table className="w-auto">
            <TableHeader>
              <TableRow>
                <TableHead>Level</TableHead>
                <TableHead>Mean A</TableHead>
                <TableHead>Mean B</TableHead>
                <TableHead>Same level</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {comparison.levels.map((level) => (
                <TableRow key={level.name}>
                  <TableCell>{level.name}</TableCell>
                  <TableCell>{level.meanA.toFixed(2)}</TableCell>
                  <TableCell>{level.meanB.toFixed(2)}</TableCell>
                  <TableCell>{percent(level.exactAgreement)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
        <div>
          <h2 className="mb-2 text-lg">Problems</h2>
          <Table className="w-auto">
            <TableHeader>
              <TableRow>
                <TableHead>Problem</TableHead>
                <TableHead>Only A</TableHead>
                <TableHead>Both</TableHead>
                <TableHead>Only B</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {comparison.flags.map((flag) => (
                <TableRow key={flag.flag}>
                  <TableCell>{flag.flag.toLowerCase().replaceAll('_', ' ')}</TableCell>
                  <TableCell>{flag.onlyA}</TableCell>
                  <TableCell>{flag.both}</TableCell>
                  <TableCell>{flag.onlyB}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      </div>

      <div>
        <h2 className="mb-2 text-lg">Disagreements, largest first ({comparison.disagreements.length} shown)</h2>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-[40%]">Text</TableHead>
              <TableHead>{runLabel(runA)}</TableHead>
              <TableHead>{runLabel(runB)}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {comparison.disagreements.map((disagreement) => (
              <TableRow key={`${disagreement.subjectKind}-${disagreement.a.id}`}>
                <TableCell className="align-top whitespace-normal">
                  <span className="text-xs text-muted-foreground">
                    {disagreement.subjectKind} {disagreement.quoteId !== null ? `· quote ${disagreement.quoteId}` : '· unapproved'}
                  </span>
                  <div>{disagreement.subjectText}</div>
                </TableCell>
                <TableCell className="align-top whitespace-normal">
                  <AssessmentSummary assessment={disagreement.a} />
                </TableCell>
                <TableCell className="align-top whitespace-normal">
                  <AssessmentSummary assessment={disagreement.b} />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </div>
  )
}

/** Quality runs and their comparison (docs/features/quote-quality.md). Read-only: acting on verdicts happens on the quotes page. */
export function QualityRunsPage() {
  const [runs, setRuns] = useState<QualityRun[]>([])
  const [runA, setRunA] = useState('')
  const [runB, setRunB] = useState('')
  const [comparison, setComparison] = useState<QualityComparison | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchQualityRuns()
      .then(setRuns)
      .catch((cause) => setError(cause instanceof Error ? cause.message : String(cause)))
  }, [])

  const compare = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setComparison(await fetchQualityComparison(Number(runA), Number(runB)))
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause))
    } finally {
      setLoading(false)
    }
  }, [runA, runB])

  const selectedA = runs.find((run) => String(run.id) === runA)
  const selectedB = runs.find((run) => String(run.id) === runB)

  return (
    <div className="flex flex-col gap-6">
      {error && (
        <Alert variant="destructive" className="border-destructive/30 bg-destructive/10">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Run</TableHead>
            <TableHead>Runner</TableHead>
            <TableHead>Model</TableHead>
            <TableHead>Prompt</TableHead>
            <TableHead>Assessed</TableHead>
            {QUALITY_VERDICTS.map((verdict) => (
              <TableHead key={verdict}>{verdict}</TableHead>
            ))}
            <TableHead>Failed</TableHead>
            <TableHead>Created</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {runs.map((run) => (
            <TableRow key={run.id}>
              <TableCell className="whitespace-normal">
                {runLabel(run)}
                {run.notes && <div className="text-xs text-muted-foreground">{run.notes}</div>}
              </TableCell>
              <TableCell>{run.runner}</TableCell>
              <TableCell className="whitespace-normal">{run.modelId}</TableCell>
              <TableCell>{run.promptVersion}</TableCell>
              <TableCell>{run.assessedCount}</TableCell>
              {QUALITY_VERDICTS.map((verdict) => (
                <TableCell key={verdict}>{run.verdictCounts[verdict] ?? 0}</TableCell>
              ))}
              <TableCell>{run.failedCount}</TableCell>
              <TableCell>{run.createdAt.slice(0, 16).replace('T', ' ')}</TableCell>
            </TableRow>
          ))}
          {runs.length === 0 && (
            <TableRow>
              <TableCell colSpan={7 + QUALITY_VERDICTS.length} className="text-muted-foreground">
                No quality runs yet. Select quotes on the approved-quotes page and choose "Assess quality".
              </TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>

      <div className="flex flex-wrap items-end gap-4">
        {[
          { id: 'quality-run-a', label: 'Run A', value: runA, onChange: setRunA },
          { id: 'quality-run-b', label: 'Run B', value: runB, onChange: setRunB },
        ].map((picker) => (
          <div key={picker.id} className="flex flex-col gap-1">
            <Label htmlFor={picker.id}>{picker.label}</Label>
            <Select value={picker.value} onValueChange={picker.onChange}>
              <SelectTrigger id={picker.id} className="w-[260px]">
                <SelectValue placeholder="Choose a run" />
              </SelectTrigger>
              <SelectContent>
                {runs.map((run) => (
                  <SelectItem key={run.id} value={String(run.id)}>
                    {runLabel(run)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        ))}
        <Button type="button" onClick={compare} disabled={!runA || !runB || runA === runB || loading}>
          {loading ? 'Comparing…' : 'Compare'}
        </Button>
      </div>

      {comparison && selectedA && selectedB && <ComparisonView comparison={comparison} runA={selectedA} runB={selectedB} />}
    </div>
  )
}
