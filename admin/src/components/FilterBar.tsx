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
    <div className="filter-bar">
      <div className="status-chips">
        {STATUS_ORDER.map((status) => (
          <button
            key={status}
            type="button"
            className={`chip chip-${status}${selectedStatuses.has(status) ? ' chip-active' : ''}`}
            aria-pressed={selectedStatuses.has(status)}
            onClick={() => onToggleStatus(status)}
          >
            {status}
            <span className="chip-count">{statusCounts[status]}</span>
          </button>
        ))}
      </div>

      <div className="filter-controls">
        <label className="filter-field">
          <span>Confidence</span>
          <select
            value={confidence}
            onChange={(event) => onConfidenceChange(event.target.value as SourceConfidence | 'all')}
          >
            <option value="all">All</option>
            {CONFIDENCE_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        </label>

        <label className="filter-field">
          <span>Provider</span>
          <select value={provider} onChange={(event) => onProviderChange(event.target.value)}>
            <option value="all">All</option>
            {providers.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        </label>

        <label className="filter-field filter-search">
          <span>Search</span>
          <input
            type="search"
            value={search}
            placeholder="Quote text or author…"
            onChange={(event) => onSearchChange(event.target.value)}
          />
        </label>
      </div>
    </div>
  )
}
