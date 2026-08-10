import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import type { ProcessingStatus, SourceConfidence } from '../types'

const STATUS_ORDER: ProcessingStatus[] = ['pending', 'approved', 'rejected', 'duplicate']
const CONFIDENCE_OPTIONS: SourceConfidence[] = ['sourced', 'attributed', 'unsourced']

interface FilterBarProps {
  statusCounts: Record<ProcessingStatus, number>
  selectedStatuses: Set<ProcessingStatus>
  onToggleStatus: (status: ProcessingStatus) => void
  confidence: SourceConfidence | 'all'
  onConfidenceChange: (value: SourceConfidence | 'all') => void
  providers: string[]
  provider: string
  onProviderChange: (value: string) => void
  search: string
  onSearchChange: (value: string) => void
}

export function FilterBar({
  statusCounts,
  selectedStatuses,
  onToggleStatus,
  confidence,
  onConfidenceChange,
  providers,
  provider,
  onProviderChange,
  search,
  onSearchChange,
}: FilterBarProps) {
  return (
    <div className="mb-5 flex flex-wrap items-center justify-between gap-4 border-b border-border pb-4">
      <div className="flex flex-wrap gap-2">
        {STATUS_ORDER.map((status) => {
          const active = selectedStatuses.has(status)
          return (
            <button
              key={status}
              type="button"
              aria-pressed={active}
              onClick={() => onToggleStatus(status)}
              className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-[13px] capitalize transition-colors ${
                active ? 'border-primary bg-brand-tint text-primary' : 'border-border bg-card text-muted-foreground'
              }`}
            >
              {status}
              <span
                className={`rounded-full px-1.5 text-xs tabular-nums ${
                  active ? 'bg-primary text-primary-foreground' : 'bg-border text-foreground'
                }`}
              >
                {statusCounts[status]}
              </span>
            </button>
          )
        })}
      </div>

      <div className="flex flex-wrap gap-4">
        <div className="flex flex-col gap-1">
          <Label htmlFor="confidence-filter" className="text-xs font-normal text-muted-foreground">
            Confidence
          </Label>
          <Select value={confidence} onValueChange={(value) => onConfidenceChange(value as SourceConfidence | 'all')}>
            <SelectTrigger id="confidence-filter" size="sm" className="min-w-[140px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All</SelectItem>
              {CONFIDENCE_OPTIONS.map((option) => (
                <SelectItem key={option} value={option} className="capitalize">
                  {option}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="flex flex-col gap-1">
          <Label htmlFor="provider-filter" className="text-xs font-normal text-muted-foreground">
            Provider
          </Label>
          <Select value={provider} onValueChange={onProviderChange}>
            <SelectTrigger id="provider-filter" size="sm" className="min-w-[140px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All</SelectItem>
              {providers.map((option) => (
                <SelectItem key={option} value={option}>
                  {option}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="flex flex-col gap-1">
          <Label htmlFor="search-filter" className="text-xs font-normal text-muted-foreground">
            Search
          </Label>
          <Input
            id="search-filter"
            type="search"
            value={search}
            placeholder="Quote text or author…"
            onChange={(event) => onSearchChange(event.target.value)}
            className="min-w-[220px]"
          />
        </div>
      </div>
    </div>
  )
}
