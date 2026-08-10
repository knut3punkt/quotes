import { useEffect, useState } from 'react'
import { Input } from '@/components/ui/input'
import { searchWikiquoteAuthors } from '../api'

const MIN_QUERY_LENGTH = 2
const DEBOUNCE_MILLIS = 300

interface WikiquoteAuthorPickerProps {
  id?: string
  value: string[]
  onChange: (names: string[]) => void
}

export function WikiquoteAuthorPicker({ id, value, onChange }: WikiquoteAuthorPickerProps) {
  const [query, setQuery] = useState('')
  const [suggestions, setSuggestions] = useState<string[]>([])

  useEffect(() => {
    const trimmed = query.trim()
    if (trimmed.length < MIN_QUERY_LENGTH) {
      setSuggestions([])
      return
    }
    const timeout = setTimeout(() => {
      searchWikiquoteAuthors(trimmed)
        .then((response) => setSuggestions(response.results))
        .catch(() => setSuggestions([]))
    }, DEBOUNCE_MILLIS)
    return () => clearTimeout(timeout)
  }, [query])

  const addAuthor = (name: string) => {
    const trimmed = name.trim()
    if (trimmed.length === 0) return
    if (!value.includes(trimmed)) onChange([...value, trimmed])
    setQuery('')
    setSuggestions([])
  }

  const removeAuthor = (name: string) => {
    onChange(value.filter((existing) => existing !== name))
  }

  return (
    <div className="flex max-w-[420px] flex-col gap-2">
      <div className="relative">
        <Input
          id={id}
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              event.preventDefault()
              addAuthor(query)
            }
          }}
          placeholder="Search for an author…"
        />
        {suggestions.length > 0 && (
          <ul className="absolute z-10 mt-1 w-full rounded-lg border border-border bg-card shadow-md">
            {suggestions.map((suggestion) => (
              <li key={suggestion}>
                <button
                  type="button"
                  className="w-full px-2.5 py-1.5 text-left text-sm hover:bg-muted"
                  onClick={() => addAuthor(suggestion)}
                >
                  {suggestion}
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>

      {value.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {value.map((name) => (
            <span
              key={name}
              className="inline-flex items-center gap-1.5 rounded-full border border-primary bg-brand-tint px-3 py-1.5 text-[13px] text-primary"
            >
              {name}
              <button
                type="button"
                aria-label={`Remove ${name}`}
                onClick={() => removeAuthor(name)}
                className="text-primary hover:text-destructive"
              >
                ×
              </button>
            </span>
          ))}
        </div>
      )}
    </div>
  )
}
