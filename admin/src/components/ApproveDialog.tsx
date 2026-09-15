import { useState } from 'react'
import type { FormEvent } from 'react'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import type { ApproveImportedQuoteRequest, Author, ImportedQuote, Source, SourceType } from '../types'

interface ApproveDialogProps {
  quote: ImportedQuote
  authors: Author[]
  sources: Source[]
  sourceTypes: SourceType[]
  submitting: boolean
  error: string | null
  onCancel: () => void
  onSubmit: (request: ApproveImportedQuoteRequest) => void
}

type AuthorMode = 'existing' | 'new' | 'none'
type SourceMode = 'existing' | 'new' | 'none'

const UNSET = '__unset__'

function normalizeTitle(title: string): string {
  return title.trim().toLowerCase().replace(/\s+/g, ' ')
}

function findMatchingAuthorId(authors: Author[], rawAuthor: string | null): number | null {
  if (!rawAuthor) return null
  const needle = rawAuthor.trim().toLowerCase()
  return authors.find((author) => author.name.trim().toLowerCase() === needle)?.id ?? null
}

// Auto-computed at Wikiquote import time from section headings/citations (see
// wikiquote/SourceHintExtractor.kt server-side) — matched against existing sources so a reviewer
// isn't offered "create new" for a work already in the catalog from an earlier quote.
function findMatchingSourceId(sources: Source[], rawSourceTitle: string | null): number | null {
  if (!rawSourceTitle) return null
  const needle = normalizeTitle(rawSourceTitle)
  return sources.find((source) => normalizeTitle(source.title) === needle)?.id ?? null
}

export function ApproveDialog({
  quote,
  authors,
  sources,
  sourceTypes,
  submitting,
  error,
  onCancel,
  onSubmit,
}: ApproveDialogProps) {
  const matchingAuthorId = findMatchingAuthorId(authors, quote.rawAuthor)
  const initialAuthorMode: AuthorMode = quote.rawAuthor
    ? matchingAuthorId !== null
      ? 'existing'
      : 'new'
    : quote.sourceId !== null
      ? 'none'
      : authors.length > 0
        ? 'existing'
        : 'new'

  const matchingSourceId = quote.sourceId ?? findMatchingSourceId(sources, quote.rawSourceTitle)
  const initialSourceMode: SourceMode =
    matchingSourceId !== null ? 'existing' : quote.rawSourceTitle ? 'new' : 'none'
  const defaultSourceTypeCode = sourceTypes.find((type) => type.code === 'book')?.code ?? sourceTypes[0]?.code ?? ''

  const [text, setText] = useState(quote.rawText)
  const [authorMode, setAuthorMode] = useState<AuthorMode>(initialAuthorMode)
  const [authorId, setAuthorId] = useState<string>(matchingAuthorId !== null ? String(matchingAuthorId) : '')
  const [newAuthorName, setNewAuthorName] = useState(quote.rawAuthor ?? '')
  const [sourceMode, setSourceMode] = useState<SourceMode>(initialSourceMode)
  const [sourceId, setSourceId] = useState<string>(matchingSourceId !== null ? String(matchingSourceId) : '')
  const [newSourceTitle, setNewSourceTitle] = useState(quote.rawSourceTitle ?? '')
  const [newSourceYear, setNewSourceYear] = useState(quote.rawSourceYear !== null ? String(quote.rawSourceYear) : '')
  const [newSourceTypeCode, setNewSourceTypeCode] = useState(defaultSourceTypeCode)
  const [sourceDetail, setSourceDetail] = useState(quote.rawSourceLocation ?? '')
  const [verified, setVerified] = useState(false)

  const authorValid =
    authorMode === 'existing' ? authorId !== '' : authorMode === 'new' ? newAuthorName.trim() !== '' : true
  const sourceValid =
    sourceMode === 'existing'
      ? sourceId !== ''
      : sourceMode === 'new'
        ? newSourceTitle.trim() !== '' && newSourceTypeCode !== ''
        : true
  const canSubmit =
    text.trim() !== '' &&
    authorValid &&
    sourceValid &&
    (authorMode !== 'none' || sourceMode !== 'none') &&
    !submitting

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    if (!canSubmit) return
    onSubmit({
      text: text.trim(),
      authorId: authorMode === 'existing' ? Number(authorId) : undefined,
      newAuthorName: authorMode === 'new' ? newAuthorName.trim() : undefined,
      sourceId: sourceMode === 'existing' ? Number(sourceId) : undefined,
      newSource:
        sourceMode === 'new'
          ? {
              title: newSourceTitle.trim(),
              typeCode: newSourceTypeCode,
              year: newSourceYear.trim() !== '' ? Number(newSourceYear) : undefined,
            }
          : undefined,
      sourceDetail: sourceDetail.trim() || undefined,
      verified,
    })
  }

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open) onCancel()
      }}
    >
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Approve imported quote</DialogTitle>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="quote-text">Quote text</Label>
            <Textarea
              id="quote-text"
              value={text}
              onChange={(event) => setText(event.target.value)}
              rows={4}
              required
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label>Author</Label>
            <RadioGroup
              value={authorMode}
              onValueChange={(value) => setAuthorMode(value as AuthorMode)}
              className="flex flex-row gap-4"
            >
              <div className="flex items-center gap-2">
                <RadioGroupItem value="existing" id="author-mode-existing" />
                <Label htmlFor="author-mode-existing" className="font-normal">
                  Existing author
                </Label>
              </div>
              <div className="flex items-center gap-2">
                <RadioGroupItem value="new" id="author-mode-new" />
                <Label htmlFor="author-mode-new" className="font-normal">
                  New author
                </Label>
              </div>
              <div className="flex items-center gap-2">
                <RadioGroupItem value="none" id="author-mode-none" />
                <Label htmlFor="author-mode-none" className="font-normal">
                  No individual author
                </Label>
              </div>
            </RadioGroup>

            {authorMode === 'none' ? (
              <p className="text-xs text-muted-foreground">A source is required when there's no individual author.</p>
            ) : authorMode === 'existing' ? (
              <Select value={authorId || UNSET} onValueChange={(value) => setAuthorId(value === UNSET ? '' : value)}>
                <SelectTrigger className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={UNSET}>Select an author…</SelectItem>
                  {authors.map((author) => (
                    <SelectItem key={author.id} value={String(author.id)}>
                      {author.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            ) : (
              <Input
                type="text"
                value={newAuthorName}
                onChange={(event) => setNewAuthorName(event.target.value)}
                placeholder="Author name"
              />
            )}
          </div>

          <div className="flex flex-col gap-1.5">
            <Label>{authorMode === 'none' ? 'Source' : 'Source (optional)'}</Label>
            <RadioGroup
              value={sourceMode}
              onValueChange={(value) => setSourceMode(value as SourceMode)}
              className="flex flex-row gap-4"
            >
              <div className="flex items-center gap-2">
                <RadioGroupItem value="existing" id="source-mode-existing" />
                <Label htmlFor="source-mode-existing" className="font-normal">
                  Existing source
                </Label>
              </div>
              <div className="flex items-center gap-2">
                <RadioGroupItem value="new" id="source-mode-new" />
                <Label htmlFor="source-mode-new" className="font-normal">
                  New source
                </Label>
              </div>
              <div className="flex items-center gap-2">
                <RadioGroupItem value="none" id="source-mode-none" />
                <Label htmlFor="source-mode-none" className="font-normal">
                  None
                </Label>
              </div>
            </RadioGroup>

            {sourceMode === 'existing' ? (
              <Select value={sourceId || UNSET} onValueChange={(value) => setSourceId(value === UNSET ? '' : value)}>
                <SelectTrigger className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={UNSET}>Select a source…</SelectItem>
                  {sources.map((source) => (
                    <SelectItem key={source.id} value={String(source.id)}>
                      {source.title}
                      {source.translation ? ` — ${source.translation}` : ''}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            ) : sourceMode === 'new' ? (
              <div className="flex flex-col gap-2">
                <Input
                  type="text"
                  value={newSourceTitle}
                  onChange={(event) => setNewSourceTitle(event.target.value)}
                  placeholder="Source title"
                />
                <div className="flex flex-row gap-2">
                  <Select value={newSourceTypeCode} onValueChange={setNewSourceTypeCode}>
                    <SelectTrigger className="w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {sourceTypes.map((type) => (
                        <SelectItem key={type.code} value={type.code}>
                          {type.description}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <Input
                    type="number"
                    value={newSourceYear}
                    onChange={(event) => setNewSourceYear(event.target.value)}
                    placeholder="Year"
                    className="w-28"
                  />
                </div>
              </div>
            ) : null}
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="source-detail">Source detail (optional)</Label>
            <Input
              id="source-detail"
              type="text"
              value={sourceDetail}
              onChange={(event) => setSourceDetail(event.target.value)}
              placeholder="e.g. chapter, page, citation"
            />
          </div>

          <div className="flex items-center gap-2">
            <Checkbox
              id="verified-checkbox"
              checked={verified}
              onCheckedChange={(checked) => setVerified(checked === true)}
            />
            <Label htmlFor="verified-checkbox" className="font-normal">
              Mark as verified
            </Label>
          </div>

          {error && (
            <Alert variant="destructive" className="border-destructive/30 bg-destructive/10">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={onCancel} disabled={submitting}>
              Cancel
            </Button>
            <Button type="submit" disabled={!canSubmit}>
              {submitting ? 'Approving…' : 'Approve'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
