import { useState } from 'react'
import type { FormEvent } from 'react'
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
    <div className="dialog-backdrop" onClick={onCancel}>
      <div
        className="dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="approve-dialog-title"
        onClick={(event) => event.stopPropagation()}
      >
        <h2 id="approve-dialog-title">Approve imported quote</h2>

        <form onSubmit={handleSubmit}>
          <label className="dialog-field">
            <span>Quote text</span>
            <textarea value={text} onChange={(event) => setText(event.target.value)} rows={4} required />
          </label>

          <fieldset className="dialog-field">
            <legend>Author</legend>
            <div className="author-mode-toggle">
              <label>
                <input
                  type="radio"
                  name="author-mode"
                  checked={authorMode === 'existing'}
                  onChange={() => setAuthorMode('existing')}
                />
                Existing author
              </label>
              <label>
                <input
                  type="radio"
                  name="author-mode"
                  checked={authorMode === 'new'}
                  onChange={() => setAuthorMode('new')}
                />
                New author
              </label>
            </div>

            {authorMode === 'existing' ? (
              <select value={authorId} onChange={(event) => setAuthorId(event.target.value)}>
                <option value="">Select an author…</option>
                {authors.map((author) => (
                  <option key={author.id} value={author.id}>
                    {author.name}
                  </option>
                ))}
              </select>
            ) : (
              <input
                type="text"
                value={newAuthorName}
                onChange={(event) => setNewAuthorName(event.target.value)}
                placeholder="Author name"
              />
            )}
          </fieldset>

          <label className="dialog-field">
            <span>Source (optional)</span>
            <select value={sourceId} onChange={(event) => setSourceId(event.target.value)}>
              <option value="">None</option>
              {sources.map((source) => (
                <option key={source.id} value={source.id}>
                  {source.title}
                </option>
              ))}
            </select>
          </label>

          <label className="dialog-field">
            <span>Source detail (optional)</span>
            <input
              type="text"
              value={sourceDetail}
              onChange={(event) => setSourceDetail(event.target.value)}
              placeholder="e.g. chapter, page, citation"
            />
          </label>

          <label className="dialog-field dialog-field-inline">
            <input type="checkbox" checked={verified} onChange={(event) => setVerified(event.target.checked)} />
            <span>Mark as verified</span>
          </label>

          {error && <p className="banner banner-error">{error}</p>}

          <div className="dialog-actions">
            <button type="button" onClick={onCancel} disabled={submitting}>
              Cancel
            </button>
            <button type="submit" disabled={!canSubmit}>
              {submitting ? 'Approving…' : 'Approve'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
