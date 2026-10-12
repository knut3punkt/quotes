import { memo } from 'react'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import type { QualityAssessment, QualityVerdict } from '../types'

export const VERDICT_CLASSES: Record<QualityVerdict, string> = {
  excellent: 'bg-emerald-100 text-emerald-900 dark:bg-emerald-900/40 dark:text-emerald-200',
  good: 'bg-green-100 text-green-900 dark:bg-green-900/40 dark:text-green-200',
  acceptable: 'bg-slate-100 text-slate-800 dark:bg-slate-800/60 dark:text-slate-200',
  weak: 'bg-amber-100 text-amber-900 dark:bg-amber-900/40 dark:text-amber-200',
  reject: 'bg-red-100 text-red-900 dark:bg-red-900/40 dark:text-red-200',
}

function levels(assessment: QualityAssessment): string {
  return [
    `stands alone ${assessment.standsAlone}`,
    `complete ${assessment.completeness}`,
    `substance ${assessment.substance}`,
    `expression ${assessment.expression}`,
  ].join(' · ')
}

function Badge({ assessment }: { assessment: QualityAssessment }) {
  const label = assessment.subjectKind === 'excerpt' ? 'excerpt' : 'quote'
  const verdict = assessment.verdict
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <span
          className={`inline-flex cursor-default items-center rounded px-1.5 py-0.5 text-xs ${
            verdict ? VERDICT_CLASSES[verdict] : 'bg-muted text-muted-foreground'
          }`}
        >
          {label}: {verdict ?? 'failed'}
        </span>
      </TooltipTrigger>
      <TooltipContent className="max-w-sm whitespace-normal">
        {assessment.subjectKind === 'excerpt' && <p className="mb-1 italic">“{assessment.subjectText}”</p>}
        {verdict ? (
          <>
            <p>{assessment.assessment}</p>
            <p className="mt-1 opacity-80">{levels(assessment)}</p>
            {assessment.problems.length > 0 && <p className="mt-1">Problems: {assessment.problems.join(', ')}</p>}
          </>
        ) : (
          <p>{assessment.errorMessage}</p>
        )}
        {assessment.precheckFlags.length > 0 && <p className="mt-1 opacity-80">Prechecks: {assessment.precheckFlags.join(', ')}</p>}
      </TooltipContent>
    </Tooltip>
  )
}

/** The verdicts a quality run gave a quote and its excerpts, with the reasoning on hover. */
function QualityVerdictBadgesComponent({ assessments }: { assessments: QualityAssessment[] }) {
  if (assessments.length === 0) return <span className="text-xs text-muted-foreground">not assessed</span>
  const ordered = [...assessments].sort((a, b) => (a.subjectKind === b.subjectKind ? 0 : a.subjectKind === 'quote' ? -1 : 1))
  return (
    <div className="flex flex-wrap gap-1">
      {ordered.map((assessment) => (
        <Badge key={assessment.id} assessment={assessment} />
      ))}
    </div>
  )
}

export const QualityVerdictBadges = memo(QualityVerdictBadgesComponent)
