import { memo } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { isDefaultQuoteFilters, type QuoteFilterState } from '../quoteFilters'
import type { EnrichmentFilter, FilterOption, LengthFilterOp, QuoteFilterOptions } from '../types'
import { FilterField } from './FilterField'
import { MultiSelectFilter } from './MultiSelectFilter'

const ENRICHMENT_OPTIONS: FilterOption[] = [
  { value: 'has', label: 'Has' },
  { value: 'none', label: 'None found' },
  { value: 'notRun', label: 'Not run' },
]

interface QuotesFilterBarProps {
  filters: QuoteFilterState
  onChange: (patch: Partial<QuoteFilterState>) => void
  onReset: () => void
  options: QuoteFilterOptions
  tagNames: string[]
}

function capitalized(options: FilterOption[]): FilterOption[] {
  return options.map((option) => ({ ...option, label: option.label.charAt(0).toUpperCase() + option.label.slice(1) }))
}

function QuotesFilterBarComponent({ filters, onChange, onReset, options, tagNames }: QuotesFilterBarProps) {
  return (
    <div className="mb-5 flex flex-col gap-4 border-b border-border pb-4">
      <div className="flex flex-wrap items-end gap-4">
        <FilterField id="quotes-search" label="Search">
          <Input
            id="quotes-search"
            type="search"
            value={filters.search}
            placeholder="Quote text or author…"
            onChange={(event) => onChange({ search: event.target.value })}
            className="min-w-[220px]"
          />
        </FilterField>

        <FilterField id="quotes-author" label="Author">
          <MultiSelectFilter
            id="quotes-author"
            options={options.authors}
            selected={filters.authors}
            onChange={(authors) => onChange({ authors })}
            searchPlaceholder="Find author…"
          />
        </FilterField>

        <FilterField id="quotes-source" label="Source">
          <MultiSelectFilter
            id="quotes-source"
            options={options.sources}
            selected={filters.sources}
            onChange={(sources) => onChange({ sources })}
            searchPlaceholder="Find source…"
          />
        </FilterField>

        <FilterField id="quotes-language" label="Language">
          <MultiSelectFilter
            id="quotes-language"
            options={options.languages}
            selected={filters.languages}
            onChange={(languages) => onChange({ languages })}
            className="min-w-[100px] max-w-[160px]"
          />
        </FilterField>

        <FilterField id="quotes-provider" label="Provider">
          <MultiSelectFilter
            id="quotes-provider"
            options={options.providers}
            selected={filters.providers}
            onChange={(providers) => onChange({ providers })}
            className="min-w-[140px] max-w-[200px]"
          />
        </FilterField>

        <FilterField id="quotes-confidence" label="Confidence">
          <MultiSelectFilter
            id="quotes-confidence"
            options={capitalized(options.sourceConfidences)}
            selected={filters.confidences}
            onChange={(confidences) => onChange({ confidences })}
            className="min-w-[140px] max-w-[200px]"
          />
        </FilterField>

        <FilterField id="quotes-length-value" label="Length">
          <div className="flex gap-1">
            <Select value={filters.lengthOp} onValueChange={(value) => onChange({ lengthOp: value as LengthFilterOp })}>
              <SelectTrigger id="quotes-length-op" size="sm" className="w-[100px]">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="above">Above</SelectItem>
                <SelectItem value="below">Below</SelectItem>
              </SelectContent>
            </Select>
            <Input
              id="quotes-length-value"
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

      <div className="flex flex-wrap items-end gap-4">
        <FilterField id="quotes-tag" label="Tag">
          <Input
            id="quotes-tag"
            type="search"
            list="quotes-tag-options"
            value={filters.tag}
            placeholder="Tag name…"
            onChange={(event) => onChange({ tag: event.target.value })}
            className="min-w-[180px]"
          />
          <datalist id="quotes-tag-options">
            {tagNames.map((name) => (
              <option key={name} value={name} />
            ))}
          </datalist>
        </FilterField>

        <FilterField id="quotes-excerpts" label="Excerpts">
          <MultiSelectFilter
            id="quotes-excerpts"
            options={ENRICHMENT_OPTIONS}
            selected={filters.excerpts}
            onChange={(excerpts) => onChange({ excerpts: excerpts as Set<EnrichmentFilter> })}
            className="min-w-[130px] max-w-[180px]"
          />
        </FilterField>

        <FilterField id="quotes-interpretations" label="Interpretations">
          <MultiSelectFilter
            id="quotes-interpretations"
            options={ENRICHMENT_OPTIONS}
            selected={filters.interpretations}
            onChange={(interpretations) =>
              onChange({ interpretations: interpretations as Set<EnrichmentFilter> })
            }
            className="min-w-[130px] max-w-[180px]"
          />
        </FilterField>

        <FilterField id="quotes-tags-status" label="Tags">
          <MultiSelectFilter
            id="quotes-tags-status"
            options={ENRICHMENT_OPTIONS}
            selected={filters.tags}
            onChange={(tags) => onChange({ tags: tags as Set<EnrichmentFilter> })}
            className="min-w-[130px] max-w-[180px]"
          />
        </FilterField>

        {!isDefaultQuoteFilters(filters) && (
          <Button type="button" variant="outline" size="sm" onClick={onReset}>
            Reset filters
          </Button>
        )}
      </div>
    </div>
  )
}

export const QuotesFilterBar = memo(QuotesFilterBarComponent)
