import { memo, type ReactNode } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import type {
  Author,
  EnrichmentFilter,
  LengthFilterOp,
  QuoteFilterOptions,
  Source,
  SourceConfidence,
} from '../types'

const CONFIDENCE_OPTIONS: SourceConfidence[] = ['sourced', 'attributed', 'unsourced', 'disputed']

const ENRICHMENT_OPTIONS: { value: EnrichmentFilter; label: string }[] = [
  { value: 'has', label: 'Has' },
  { value: 'none', label: 'None found' },
  { value: 'notRun', label: 'Not run' },
]

/** Raw control values; `'all'` stands for "no filter" because Radix Select doesn't allow an empty value. */
export interface QuoteFilterState {
  search: string
  authorId: 'all' | string
  sourceId: 'all' | string
  language: 'all' | string
  provider: 'all' | string
  confidence: 'all' | SourceConfidence
  lengthOp: LengthFilterOp
  lengthValue: string
  tag: string
  excerpts: 'all' | EnrichmentFilter
  interpretations: 'all' | EnrichmentFilter
  tags: 'all' | EnrichmentFilter
}

export const DEFAULT_QUOTE_FILTERS: QuoteFilterState = {
  search: '',
  authorId: 'all',
  sourceId: 'all',
  language: 'all',
  provider: 'all',
  confidence: 'all',
  lengthOp: 'above',
  lengthValue: '',
  tag: '',
  excerpts: 'all',
  interpretations: 'all',
  tags: 'all',
}

interface QuotesFilterBarProps {
  filters: QuoteFilterState
  onChange: (patch: Partial<QuoteFilterState>) => void
  onReset: () => void
  authors: Author[]
  sources: Source[]
  options: QuoteFilterOptions
  tagNames: string[]
}

function FilterField({ id, label, children }: { id: string; label: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1">
      <Label htmlFor={id} className="text-xs font-normal text-muted-foreground">
        {label}
      </Label>
      {children}
    </div>
  )
}

function FilterSelect({
  id,
  value,
  onValueChange,
  options,
  className = 'min-w-[140px]',
}: {
  id: string
  value: string
  onValueChange: (value: string) => void
  options: { value: string; label: string }[]
  className?: string
}) {
  return (
    <Select value={value} onValueChange={onValueChange}>
      <SelectTrigger id={id} size="sm" className={className}>
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value="all">All</SelectItem>
        {options.map((option) => (
          <SelectItem key={option.value} value={option.value}>
            {option.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}

function asOptions(values: string[]): { value: string; label: string }[] {
  return values.map((value) => ({ value, label: value }))
}

function QuotesFilterBarComponent({
  filters,
  onChange,
  onReset,
  authors,
  sources,
  options,
  tagNames,
}: QuotesFilterBarProps) {
  const isDefault = (Object.keys(DEFAULT_QUOTE_FILTERS) as (keyof QuoteFilterState)[]).every(
    (key) => filters[key] === DEFAULT_QUOTE_FILTERS[key],
  )

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
          <FilterSelect
            id="quotes-author"
            value={filters.authorId}
            onValueChange={(authorId) => onChange({ authorId })}
            options={authors.map((author) => ({ value: String(author.id), label: author.name }))}
            className="min-w-[160px]"
          />
        </FilterField>

        <FilterField id="quotes-source" label="Source">
          <FilterSelect
            id="quotes-source"
            value={filters.sourceId}
            onValueChange={(sourceId) => onChange({ sourceId })}
            options={sources.map((source) => ({ value: String(source.id), label: source.title }))}
            className="min-w-[160px] max-w-[260px]"
          />
        </FilterField>

        <FilterField id="quotes-language" label="Language">
          <FilterSelect
            id="quotes-language"
            value={filters.language}
            onValueChange={(language) => onChange({ language })}
            options={asOptions(options.languages)}
            className="min-w-[100px]"
          />
        </FilterField>

        <FilterField id="quotes-provider" label="Provider">
          <FilterSelect
            id="quotes-provider"
            value={filters.provider}
            onValueChange={(provider) => onChange({ provider })}
            options={asOptions(options.providers)}
          />
        </FilterField>

        <FilterField id="quotes-confidence" label="Confidence">
          <FilterSelect
            id="quotes-confidence"
            value={filters.confidence}
            onValueChange={(confidence) => onChange({ confidence: confidence as QuoteFilterState['confidence'] })}
            options={CONFIDENCE_OPTIONS.map((option) => ({
              value: option,
              label: option.charAt(0).toUpperCase() + option.slice(1),
            }))}
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
          <FilterSelect
            id="quotes-excerpts"
            value={filters.excerpts}
            onValueChange={(excerpts) => onChange({ excerpts: excerpts as QuoteFilterState['excerpts'] })}
            options={ENRICHMENT_OPTIONS}
            className="min-w-[130px]"
          />
        </FilterField>

        <FilterField id="quotes-interpretations" label="Interpretations">
          <FilterSelect
            id="quotes-interpretations"
            value={filters.interpretations}
            onValueChange={(interpretations) =>
              onChange({ interpretations: interpretations as QuoteFilterState['interpretations'] })
            }
            options={ENRICHMENT_OPTIONS}
            className="min-w-[130px]"
          />
        </FilterField>

        <FilterField id="quotes-tags-status" label="Tags">
          <FilterSelect
            id="quotes-tags-status"
            value={filters.tags}
            onValueChange={(tags) => onChange({ tags: tags as QuoteFilterState['tags'] })}
            options={ENRICHMENT_OPTIONS}
            className="min-w-[130px]"
          />
        </FilterField>

        {!isDefault && (
          <Button type="button" variant="outline" size="sm" onClick={onReset}>
            Reset filters
          </Button>
        )}
      </div>
    </div>
  )
}

export const QuotesFilterBar = memo(QuotesFilterBarComponent)
