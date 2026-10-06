import { memo } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { FilterField } from './FilterField'
import { MultiSelectFilter } from './MultiSelectFilter'
import {
  isDefaultImportedQuoteFilters,
  type ImportedQuoteFilterState,
} from '../importedQuoteFilters'
import type { FilterOption, LengthFilterOp, ProcessingStatus, SourceConfidence } from '../types'

const STATUS_ORDER: ProcessingStatus[] = ['pending', 'approved', 'rejected', 'duplicate']
const CONFIDENCE_OPTIONS: SourceConfidence[] = ['sourced', 'attributed', 'unsourced', 'disputed']

interface FilterBarProps {
  filters: ImportedQuoteFilterState
  onChange: (patch: Partial<ImportedQuoteFilterState>) => void
  onReset: () => void
  statusCounts: Record<ProcessingStatus, number>
  providers: string[]
  authorOptions: FilterOption[]
  sourceOptions: FilterOption[]
}

function FilterBarComponent({
  filters,
  onChange,
  onReset,
  statusCounts,
  providers,
  authorOptions,
  sourceOptions,
}: FilterBarProps) {
  const toggleStatus = (status: ProcessingStatus) => {
    const statuses = new Set(filters.statuses)
    if (statuses.has(status)) statuses.delete(status)
    else statuses.add(status)
    onChange({ statuses })
  }

  return (
    <div className="mb-5 flex flex-col gap-4 border-b border-border pb-4">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex flex-wrap gap-2">
          {STATUS_ORDER.map((status) => {
            const active = filters.statuses.has(status)
            return (
              <button
                key={status}
                type="button"
                aria-pressed={active}
                onClick={() => toggleStatus(status)}
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

        {!isDefaultImportedQuoteFilters(filters) && (
          <Button type="button" variant="outline" size="sm" onClick={onReset}>
            Reset filters
          </Button>
        )}
      </div>

      <div className="flex flex-wrap items-end gap-4">
        <FilterField id="search-filter" label="Search">
          <Input
            id="search-filter"
            type="search"
            value={filters.search}
            placeholder="Quote text or author…"
            onChange={(event) => onChange({ search: event.target.value })}
            className="min-w-[220px]"
          />
        </FilterField>

        <FilterField id="author-filter" label="Author">
          <MultiSelectFilter
            id="author-filter"
            options={authorOptions}
            selected={filters.authors}
            onChange={(authors) => onChange({ authors })}
            searchPlaceholder="Find author…"
          />
        </FilterField>

        <FilterField id="source-filter" label="Source">
          <MultiSelectFilter
            id="source-filter"
            options={sourceOptions}
            selected={filters.sources}
            onChange={(sources) => onChange({ sources })}
            searchPlaceholder="Find source…"
          />
        </FilterField>

        <FilterField id="confidence-filter" label="Confidence">
          <Select
            value={filters.confidence}
            onValueChange={(value) => onChange({ confidence: value as ImportedQuoteFilterState['confidence'] })}
          >
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
        </FilterField>

        <FilterField id="provider-filter" label="Provider">
          <Select value={filters.provider} onValueChange={(provider) => onChange({ provider })}>
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
        </FilterField>
      </div>

      <div className="flex flex-wrap items-end gap-4">
        <FilterField id="possible-duplicate-filter" label="Possible duplicate">
          <Select
            value={filters.possibleDuplicate}
            onValueChange={(value) => onChange({ possibleDuplicate: value as ImportedQuoteFilterState['possibleDuplicate'] })}
          >
            <SelectTrigger id="possible-duplicate-filter" size="sm" className="min-w-[140px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All</SelectItem>
              <SelectItem value="flagged">Flagged</SelectItem>
              <SelectItem value="notFlagged">Not flagged</SelectItem>
            </SelectContent>
          </Select>
        </FilterField>

        <FilterField id="imported-from-filter" label="Imported">
          <div className="flex items-center gap-1">
            <Input
              id="imported-from-filter"
              type="date"
              aria-label="Imported from"
              value={filters.importedFrom}
              max={filters.importedTo || undefined}
              onChange={(event) => onChange({ importedFrom: event.target.value })}
              className="w-[150px]"
            />
            <span className="text-muted-foreground">–</span>
            <Input
              id="imported-to-filter"
              type="date"
              aria-label="Imported to"
              value={filters.importedTo}
              min={filters.importedFrom || undefined}
              onChange={(event) => onChange({ importedTo: event.target.value })}
              className="w-[150px]"
            />
          </div>
        </FilterField>

        <FilterField id="length-filter-value" label="Length">
          <div className="flex gap-1">
            <Select value={filters.lengthOp} onValueChange={(value) => onChange({ lengthOp: value as LengthFilterOp })}>
              <SelectTrigger id="length-filter-op" size="sm" className="w-[100px]">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="above">Above</SelectItem>
                <SelectItem value="below">Below</SelectItem>
              </SelectContent>
            </Select>
            <Input
              id="length-filter-value"
              type="number"
              min={0}
              inputMode="numeric"
              value={filters.lengthValue}
              placeholder="chars"
              onChange={(event) => onChange({ lengthValue: event.target.value })}
              className="w-20"
            />
          </div>
        </FilterField>
      </div>
    </div>
  )
}

export const FilterBar = memo(FilterBarComponent)
