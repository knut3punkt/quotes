import { memo, useMemo, useState } from 'react'
import { ChevronDownIcon } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import type { TagSummary } from '../types'

/** Rendering thousands of rows makes the popover sluggish; the search box narrows past this. */
const MAX_VISIBLE_OPTIONS = 300

interface TagPickerProps {
  /** Candidate tags in display order. */
  options: TagSummary[]
  /** A tag left out of the options, such as the one being merged. */
  excludeId?: number
  value: TagSummary | null
  onChange: (tag: TagSummary) => void
  ariaLabel: string
}

/**
 * A searchable single-tag picker. Its options are only built while it is open, so a table can give every row
 * one without rendering the whole vocabulary per row.
 */
function TagPickerComponent({ options, excludeId, value, onChange, ariaLabel }: TagPickerProps) {
  const [open, setOpen] = useState(false)
  const [term, setTerm] = useState('')

  const matching = useMemo(() => {
    if (!open) return []
    const needle = term.trim().toLowerCase()
    return options.filter(
      (option) => option.id !== excludeId && (!needle || option.name.toLowerCase().includes(needle)),
    )
  }, [open, options, excludeId, term])
  const visible = matching.slice(0, MAX_VISIBLE_OPTIONS)

  return (
    <Popover
      open={open}
      onOpenChange={(next) => {
        setOpen(next)
        if (!next) setTerm('')
      }}
    >
      <PopoverTrigger asChild>
        <button
          type="button"
          aria-label={ariaLabel}
          className="flex h-7 w-[180px] items-center justify-between gap-1 rounded-md border border-input bg-background px-1.5 text-xs outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
        >
          <span className={`truncate ${value ? '' : 'text-muted-foreground'}`}>{value ? value.name : 'Choose tag…'}</span>
          <ChevronDownIcon className="pointer-events-none size-3.5 shrink-0 text-muted-foreground" />
        </button>
      </PopoverTrigger>
      <PopoverContent className="flex w-[300px] max-w-[calc(100vw-2rem)] flex-col gap-2">
        <Input
          type="search"
          value={term}
          placeholder="Find tag…"
          onChange={(event) => setTerm(event.target.value)}
          autoFocus
        />
        <div className="max-h-[320px] overflow-y-auto">
          {visible.length === 0 && <p className="px-2 py-3 text-center text-sm text-muted-foreground">No matches</p>}
          {visible.map((option) => (
            <button
              key={option.id}
              type="button"
              className={`flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm hover:bg-accent ${
                option.id === value?.id ? 'bg-accent' : ''
              }`}
              onClick={() => {
                onChange(option)
                setOpen(false)
                setTerm('')
              }}
            >
              <span className="min-w-0 flex-1 truncate" title={option.name}>
                {option.name}
              </span>
              <span className="text-xs text-muted-foreground tabular-nums">{option.usageCount}</span>
            </button>
          ))}
          {matching.length > visible.length && (
            <p className="px-2 py-2 text-xs text-muted-foreground">
              {matching.length - visible.length} more — type to narrow the list.
            </p>
          )}
        </div>
      </PopoverContent>
    </Popover>
  )
}

export const TagPicker = memo(TagPickerComponent)
