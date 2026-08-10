import { Fragment, useEffect, useRef, useState } from 'react'
import { canApprove, canMarkDuplicate, canReject, canResetToPending } from '../statusRules'
import type { ImportedQuote } from '../types'

interface ImportedQuotesTableProps {
  quotes: ImportedQuote[]
  busyId: number | null
  bulkBusy: boolean
  selectedIds: Set<number>
  allVisibleSelected: boolean
  onToggleSelect: (id: number) => void
  onToggleSelectAllVisible: () => void
  onApprove: (quote: ImportedQuote) => void
  onReject: (quote: ImportedQuote) => void
  onMarkDuplicate: (quote: ImportedQuote) => void
  onResetToPending: (quote: ImportedQuote) => void
}

function truncate(text: string, maxLength: number): string {
  return text.length > maxLength ? `${text.slice(0, maxLength).trimEnd()}…` : text
}

function payloadString(payload: Record<string, unknown>, key: string): string | undefined {
  const value = payload[key]
  return typeof value === 'string' ? value : undefined
}

function payloadStringArray(payload: Record<string, unknown>, key: string): string[] {
  const value = payload[key]
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : []
}

export function ImportedQuotesTable({
  quotes,
  busyId,
  bulkBusy,
  selectedIds,
  allVisibleSelected,
  onToggleSelect,
  onToggleSelectAllVisible,
  onApprove,
  onReject,
  onMarkDuplicate,
  onResetToPending,
}: ImportedQuotesTableProps) {
  const [expandedIds, setExpandedIds] = useState<Set<number>>(new Set())
  const selectAllRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (selectAllRef.current) {
      const someSelected = quotes.some((quote) => selectedIds.has(quote.id))
      selectAllRef.current.indeterminate = someSelected && !allVisibleSelected
    }
  }, [quotes, selectedIds, allVisibleSelected])

  const toggleExpanded = (id: number) => {
    setExpandedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  if (quotes.length === 0) {
    return <p className="status-message">No imported quotes match the current filters.</p>
  }

  return (
    <table className="quotes-table">
      <thead>
        <tr>
          <th scope="col" className="cell-select">
            <label className="checkbox-hit">
              <input
                ref={selectAllRef}
                type="checkbox"
                checked={allVisibleSelected}
                onChange={onToggleSelectAllVisible}
                disabled={bulkBusy}
                aria-label="Select all visible rows"
              />
            </label>
          </th>
          <th scope="col">Quote</th>
          <th scope="col">Author</th>
          <th scope="col">Provider</th>
          <th scope="col">Confidence</th>
          <th scope="col">Status</th>
          <th scope="col">Imported</th>
          <th scope="col">Actions</th>
        </tr>
      </thead>
      <tbody>
        {quotes.map((quote) => {
          const expanded = expandedIds.has(quote.id)
          const busy = busyId === quote.id || bulkBusy
          const pageUrl = payloadString(quote.rawPayload, 'pageUrl')
          const citations = payloadStringArray(quote.rawPayload, 'citations')

          return (
            <Fragment key={quote.id}>
              <tr className={selectedIds.has(quote.id) ? 'row-selected' : undefined}>
                <td className="cell-select">
                  <label className="checkbox-hit">
                    <input
                      type="checkbox"
                      checked={selectedIds.has(quote.id)}
                      onChange={() => onToggleSelect(quote.id)}
                      disabled={bulkBusy}
                      aria-label={`Select quote ${quote.id}`}
                    />
                  </label>
                </td>
                <td className="cell-text">
                  <button type="button" className="text-toggle" onClick={() => toggleExpanded(quote.id)}>
                    {expanded ? truncate(quote.rawText, 500) : truncate(quote.rawText, 90)}
                  </button>
                </td>
                <td>{quote.rawAuthor ?? '—'}</td>
                <td>{quote.provider}</td>
                <td>
                  {quote.sourceConfidence ? (
                    <span className={`badge badge-confidence-${quote.sourceConfidence}`}>
                      {quote.sourceConfidence}
                    </span>
                  ) : (
                    '—'
                  )}
                </td>
                <td>
                  <span className={`badge badge-status-${quote.processingStatus}`}>
                    {quote.processingStatus}
                  </span>
                </td>
                <td className="cell-date">{new Date(quote.importedAt).toLocaleString()}</td>
                <td className="cell-actions">
                  {quote.processingStatus === 'approved' ? (
                    <span className="approved-note">Quote #{quote.quoteId}</span>
                  ) : (
                    <>
                      {canApprove(quote.processingStatus) && (
                        <button type="button" disabled={busy} onClick={() => onApprove(quote)}>
                          Approve
                        </button>
                      )}
                      {canReject(quote.processingStatus) && (
                        <button type="button" disabled={busy} onClick={() => onReject(quote)}>
                          Reject
                        </button>
                      )}
                      {canMarkDuplicate(quote.processingStatus) && (
                        <button type="button" disabled={busy} onClick={() => onMarkDuplicate(quote)}>
                          Mark duplicate
                        </button>
                      )}
                      {canResetToPending(quote.processingStatus) && (
                        <button type="button" disabled={busy} onClick={() => onResetToPending(quote)}>
                          Reset
                        </button>
                      )}
                    </>
                  )}
                </td>
              </tr>
              {expanded && (
                <tr className="detail-row">
                  <td colSpan={8}>
                    <div className="detail-panel">
                      <p>
                        <strong>Full text:</strong> {quote.rawText}
                      </p>
                      {pageUrl && (
                        <p>
                          <strong>Source page:</strong>{' '}
                          <a href={pageUrl} target="_blank" rel="noreferrer">
                            {pageUrl}
                          </a>
                        </p>
                      )}
                      {citations.length > 0 && (
                        <div>
                          <strong>Citations:</strong>
                          <ul>
                            {citations.map((citation) => (
                              <li key={citation}>{citation}</li>
                            ))}
                          </ul>
                        </div>
                      )}
                    </div>
                  </td>
                </tr>
              )}
            </Fragment>
          )
        })}
      </tbody>
    </table>
  )
}
