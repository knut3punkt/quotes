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
import type { ApproveImportedQuoteRequest, Author, ImportedQuote, Source } from '../types'

interface ApproveDialogProps {
  quote: ImportedQuote
  authors: Author[]
  sources: Source[]
  submitting: boolean
  error: string | null
  onCancel: () => void
  onSubmit: (request: ApproveImportedQuoteRequest) => void
}

type AuthorMode = 'existing' | 'new'

const UNSET = '__unset__'

export function ApproveDialog({
  quote,
  authors,
  sources,
  submitting,
  error,
  onCancel,
  onSubmit,
}: ApproveDialogProps) {
  const [text, setText] = useState(quote.rawText)
  const [authorMode, setAuthorMode] = useState<AuthorMode>(authors.length > 0 ? 'existing' : 'new')
  const [authorId, setAuthorId] = useState<string>('')
  const [newAuthorName, setNewAuthorName] = useState(quote.rawAuthor ?? '')
  const [sourceId, setSourceId] = useState<string>('')
  const [sourceDetail, setSourceDetail] = useState('')
  const [verified, setVerified] = useState(false)

  const authorValid = authorMode === 'existing' ? authorId !== '' : newAuthorName.trim() !== ''
  const canSubmit = text.trim() !== '' && authorValid && !submitting

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    if (!canSubmit) return
    onSubmit({
      text: text.trim(),
      authorId: authorMode === 'existing' ? Number(authorId) : undefined,
      newAuthorName: authorMode === 'new' ? newAuthorName.trim() : undefined,
      sourceId: sourceId !== '' ? Number(sourceId) : undefined,
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
            </RadioGroup>

            {authorMode === 'existing' ? (
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
            <Label>Source (optional)</Label>
            <Select value={sourceId || UNSET} onValueChange={(value) => setSourceId(value === UNSET ? '' : value)}>
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={UNSET}>None</SelectItem>
                {sources.map((source) => (
                  <SelectItem key={source.id} value={String(source.id)}>
                    {source.title}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
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
