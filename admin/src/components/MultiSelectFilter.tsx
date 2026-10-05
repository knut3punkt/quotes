import { memo, useMemo, useState } from 'react'
import { ChevronDownIcon } from 'lucide-react'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'

export interface MultiSelectOption {
  value: string
  label: string
  count: number
}

/** Rendering thousands of checkbox rows makes the popover sluggish; the search box narrows past this. */
const MAX_VISIBLE_OPTIONS = 300

interface MultiSelectFilterProps {
  id: string
  /** Options in display order; a "none" option, if any, is expected first. */
  options: MultiSelectOption[]
  selected: Set<string>
  onChange: (selected: Set<string>) => void
  searchPlaceholder: string
  className?: string
}

function MultiSelectFilterComponent({
  id,
  options,
  selected,
  onChange,
  searchPlaceholder,
  className = 'min-w-[160px] max-w-[240px]',
}: MultiSelectFilterProps) {
  const [term, setTerm] = useState('')

  const matching = useMemo(() => {
    const needle = term.trim().toLowerCase()
    return needle ? options.filter((option) => option.label.toLowerCase().includes(needle)) : options
  }, [options, term])
  const visible = matching.slice(0, MAX_VISIBLE_OPTIONS)

  const triggerLabel = useMemo(() => {
    if (selected.size === 0) return 'All'
    if (selected.size === 1) {
      const [value] = selected
      return options.find((option) => option.value === value)?.label ?? '1 selected'
    }
    return `${selected.size} selected`
  }, [options, selected])

  const toggle = (value: string) => {
    const next = new Set(selected)
    if (next.has(value)) next.delete(value)
    else next.add(value)
    onChange(next)
  }

  return (
    <Popover onOpenChange={(open) => !open && setTerm('')}>
      <PopoverTrigger asChild>
        <button
          id={id}
          type="button"
          className={`flex h-7 items-center justify-between gap-1.5 rounded-[min(var(--radius-md),10px)] border border-input bg-transparent py-2 pr-2 pl-2.5 text-sm whitespace-nowrap transition-colors outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30 dark:hover:bg-input/50 ${className}`}
        >
          <span className="truncate">{triggerLabel}</span>
          <ChevronDownIcon className="pointer-events-none size-4 shrink-0 text-muted-foreground" />
        </button>
      </PopoverTrigger>
      <PopoverContent className="flex w-[320px] flex-col gap-2">
        <Input
          type="search"
          value={term}
          placeholder={searchPlaceholder}
          onChange={(event) => setTerm(event.target.value)}
          autoFocus
        />
        <div className="max-h-[320px] overflow-y-auto">
          {visible.length === 0 && <p className="px-2 py-3 text-center text-sm text-muted-foreground">No matches</p>}
          {visible.map((option) => (
            <label
              key={option.value}
              className="flex cursor-pointer items-center gap-2 rounded-md px-2 py-1.5 text-sm hover:bg-accent"
            >
              <Checkbox checked={selected.has(option.value)} onCheckedChange={() => toggle(option.value)} />
              <span className="min-w-0 flex-1 truncate" title={option.label}>
                {option.label}
              </span>
              <span className="text-xs text-muted-foreground tabular-nums">{option.count}</span>
            </label>
          ))}
          {matching.length > visible.length && (
            <p className="px-2 py-2 text-xs text-muted-foreground">
              {matching.length - visible.length} more — type to narrow the list.
            </p>
          )}
        </div>
        <div className="flex items-center justify-between border-t border-border pt-2 text-xs text-muted-foreground">
          <span>{selected.size} selected</span>
          <button
            type="button"
            className="text-primary disabled:opacity-50"
            disabled={selected.size === 0}
            onClick={() => onChange(new Set())}
          >
            Clear
          </button>
        </div>
      </PopoverContent>
    </Popover>
  )
}

export const MultiSelectFilter = memo(MultiSelectFilterComponent)
