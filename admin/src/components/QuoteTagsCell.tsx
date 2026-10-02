import { memo, useState } from 'react'
import { Loader2, Plus, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { addQuoteTag, rejectQuoteTag } from '../api'
import { toast } from '../hooks/use-toast'
import { FACET_LABELS, TAG_FACETS, tagChipClassName, tagChipTitle } from '../tagStyles'
import type { QuoteExcerpt, QuoteTag, TagFacet } from '../types'

const FACET_ORDER: Record<TagFacet, number> = { concept: 0, mood: 1, motif: 2 }
const EXCERPT_LABEL_WORDS = 6

interface QuoteTagsCellProps {
  quoteId: number
  tags: QuoteTag[]
  excerpts: QuoteExcerpt[] // qualifying excerpts only
  vocabulary: Record<TagFacet, string[]>
  disabled: boolean
  onTagsChange: (quoteId: number, tags: QuoteTag[]) => void
}

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : 'Something went wrong'
}

function excerptLabel(excerpt: QuoteExcerpt): string {
  const words = excerpt.text.trim().split(/\s+/)
  return words.length > EXCERPT_LABEL_WORDS ? `“${words.slice(0, EXCERPT_LABEL_WORDS).join(' ')}…”` : `“${excerpt.text.trim()}”`
}

function sortTags(tags: QuoteTag[]): QuoteTag[] {
  return [...tags].sort(
    (a, b) => FACET_ORDER[a.facet] - FACET_ORDER[b.facet] || b.relevance - a.relevance || a.name.localeCompare(b.name),
  )
}

function QuoteTagsCellComponent({ quoteId, tags, excerpts, vocabulary, disabled, onTagsChange }: QuoteTagsCellProps) {
  const [removingId, setRemovingId] = useState<number | null>(null)
  const [adding, setAdding] = useState(false)
  const [formOpen, setFormOpen] = useState(false)
  const [facet, setFacet] = useState<TagFacet>('concept')
  const [name, setName] = useState('')
  const [subject, setSubject] = useState('quote')

  const subjects = [
    { key: 'quote', label: null as string | null, tags: tags.filter((tag) => tag.excerptId === null) },
    ...excerpts.map((excerpt) => ({
      key: String(excerpt.id),
      label: excerptLabel(excerpt),
      tags: tags.filter((tag) => tag.excerptId === excerpt.id),
    })),
  ].filter((group) => group.key === 'quote' || group.tags.length > 0)

  const remove = async (tag: QuoteTag) => {
    setRemovingId(tag.assignmentId)
    try {
      await rejectQuoteTag(tag.assignmentId)
      onTagsChange(quoteId, tags.filter((other) => other.assignmentId !== tag.assignmentId))
    } catch (err) {
      toast.error('Could not remove tag', errorMessage(err))
    } finally {
      setRemovingId(null)
    }
  }

  const add = async () => {
    const trimmed = name.trim()
    if (!trimmed) return
    setAdding(true)
    try {
      const excerptId = subject === 'quote' ? undefined : Number(subject)
      const added = await addQuoteTag(quoteId, { facet, name: trimmed, excerptId })
      onTagsChange(quoteId, [...tags.filter((other) => other.assignmentId !== added.assignmentId), added])
      setName('')
    } catch (err) {
      toast.error('Could not add tag', errorMessage(err))
    } finally {
      setAdding(false)
    }
  }

  const datalistId = `tag-vocabulary-${quoteId}-${facet}`

  return (
    <div className="flex min-w-[220px] flex-col gap-1.5">
      {subjects.map((group) => (
        <div key={group.key} className="flex flex-col gap-1">
          {group.label && <span className="text-[11px] text-muted-foreground">{group.label}</span>}
          <div className="flex flex-wrap gap-1">
            {group.tags.length === 0 && group.key === 'quote' && <span className="text-xs text-muted-foreground">—</span>}
            {sortTags(group.tags).map((tag) => (
              <span
                key={tag.assignmentId}
                title={tagChipTitle(tag)}
                className={`inline-flex items-center gap-0.5 rounded-full border px-2 py-0.5 text-xs ${tagChipClassName(tag)}`}
              >
                {tag.name}
                <button
                  type="button"
                  className="-mr-1 rounded-full p-0.5 hover:bg-foreground/10 disabled:opacity-50"
                  onClick={() => remove(tag)}
                  disabled={disabled || removingId !== null}
                  aria-label={`Remove tag ${tag.name}`}
                >
                  {removingId === tag.assignmentId ? <Loader2 className="h-3 w-3 animate-spin" /> : <X className="h-3 w-3" />}
                </button>
              </span>
            ))}
          </div>
        </div>
      ))}

      {formOpen ? (
        <form
          className="flex flex-wrap items-center gap-1"
          onSubmit={(event) => {
            event.preventDefault()
            void add()
          }}
        >
          <select
            className="h-7 rounded-md border border-input bg-background px-1 text-xs"
            value={facet}
            onChange={(event) => setFacet(event.target.value as TagFacet)}
            aria-label="Tag facet"
          >
            {TAG_FACETS.map((option) => (
              <option key={option} value={option}>
                {FACET_LABELS[option]}
              </option>
            ))}
          </select>
          {excerpts.length > 0 && (
            <select
              className="h-7 max-w-[140px] rounded-md border border-input bg-background px-1 text-xs"
              value={subject}
              onChange={(event) => setSubject(event.target.value)}
              aria-label="Tag the whole quote or an excerpt"
            >
              <option value="quote">Whole quote</option>
              {excerpts.map((excerpt) => (
                <option key={excerpt.id} value={String(excerpt.id)}>
                  {excerptLabel(excerpt)}
                </option>
              ))}
            </select>
          )}
          <Input
            className="h-7 w-32 text-xs"
            value={name}
            list={datalistId}
            placeholder="tag name"
            autoFocus
            onChange={(event) => setName(event.target.value)}
            aria-label="Tag name"
          />
          <datalist id={datalistId}>
            {vocabulary[facet].map((option) => (
              <option key={option} value={option} />
            ))}
          </datalist>
          <Button type="submit" size="sm" variant="outline" className="h-7" disabled={disabled || adding || !name.trim()}>
            {adding && <Loader2 className="animate-spin" />}
            Add
          </Button>
          <Button type="button" size="sm" variant="ghost" className="h-7" onClick={() => setFormOpen(false)}>
            Done
          </Button>
        </form>
      ) : (
        <button
          type="button"
          className="inline-flex w-fit items-center gap-0.5 text-xs text-muted-foreground hover:text-foreground disabled:opacity-50"
          onClick={() => setFormOpen(true)}
          disabled={disabled}
        >
          <Plus className="h-3 w-3" /> Add tag
        </button>
      )}
    </div>
  )
}

export const QuoteTagsCell = memo(QuoteTagsCellComponent)
