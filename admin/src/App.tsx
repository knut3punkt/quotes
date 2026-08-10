import { useCallback, useEffect, useMemo, useState } from 'react'
import './App.css'
import { approveImportedQuote, fetchAuthors, fetchImportedQuotes, fetchSources, updateImportedQuoteStatus } from './api'
import { ApproveDialog } from './components/ApproveDialog'
import { FilterBar } from './components/FilterBar'
import { ImportedQuotesTable } from './components/ImportedQuotesTable'
import type { ApproveImportedQuoteRequest, Author, ImportedQuote, ProcessingStatus, Source, SourceConfidence } from './types'

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : 'Something went wrong'
}

function App() {
  const [importedQuotes, setImportedQuotes] = useState<ImportedQuote[]>([])
  const [authors, setAuthors] = useState<Author[]>([])
  const [sources, setSources] = useState<Source[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [selectedStatuses, setSelectedStatuses] = useState<Set<ProcessingStatus>>(new Set(['pending']))
  const [confidence, setConfidence] = useState<SourceConfidence | 'all'>('all')
  const [provider, setProvider] = useState('all')
  const [search, setSearch] = useState('')

  const [approveTarget, setApproveTarget] = useState<ImportedQuote | null>(null)
  const [approveSubmitting, setApproveSubmitting] = useState(false)
  const [approveError, setApproveError] = useState<string | null>(null)

  const [busyId, setBusyId] = useState<number | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const loadAll = useCallback(async () => {
    setLoading(true)
    setLoadError(null)
    try {
      const [quotes, authorList, sourceList] = await Promise.all([
        fetchImportedQuotes(),
        fetchAuthors(),
        fetchSources(),
      ])
      setImportedQuotes(quotes)
      setAuthors(authorList)
      setSources(sourceList)
    } catch (err) {
      setLoadError(errorMessage(err))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadAll()
  }, [loadAll])

  const statusCounts = useMemo(() => {
    const counts: Record<ProcessingStatus, number> = { pending: 0, approved: 0, rejected: 0, duplicate: 0 }
    for (const quote of importedQuotes) counts[quote.processingStatus] += 1
    return counts
  }, [importedQuotes])

  const providers = useMemo(
    () => Array.from(new Set(importedQuotes.map((quote) => quote.provider))).sort(),
    [importedQuotes],
  )

  const filteredQuotes = useMemo(() => {
    const term = search.trim().toLowerCase()
    return importedQuotes.filter((quote) => {
      if (!selectedStatuses.has(quote.processingStatus)) return false
      if (confidence !== 'all' && quote.sourceConfidence !== confidence) return false
      if (provider !== 'all' && quote.provider !== provider) return false
      if (term) {
        const haystack = `${quote.rawText} ${quote.rawAuthor ?? ''}`.toLowerCase()
        if (!haystack.includes(term)) return false
      }
      return true
    })
  }, [importedQuotes, selectedStatuses, confidence, provider, search])

  const toggleStatus = (status: ProcessingStatus) => {
    setSelectedStatuses((prev) => {
      const next = new Set(prev)
      if (next.has(status)) next.delete(status)
      else next.add(status)
      return next
    })
  }

  const runStatusAction = async (quote: ImportedQuote, status: ProcessingStatus) => {
    setBusyId(quote.id)
    setActionError(null)
    try {
      const updated = await updateImportedQuoteStatus(quote.id, status)
      setImportedQuotes((prev) => prev.map((existing) => (existing.id === updated.id ? updated : existing)))
    } catch (err) {
      setActionError(errorMessage(err))
    } finally {
      setBusyId(null)
    }
  }

  const handleApproveSubmit = async (request: ApproveImportedQuoteRequest) => {
    if (!approveTarget) return
    setApproveSubmitting(true)
    setApproveError(null)
    try {
      await approveImportedQuote(approveTarget.id, request)
      setApproveTarget(null)
      await loadAll()
    } catch (err) {
      setApproveError(errorMessage(err))
    } finally {
      setApproveSubmitting(false)
    }
  }

  return (
    <div className="page">
      <header className="page-header">
        <h1>Imported quotes</h1>
        <p>Review staged imports and promote them into the quote library.</p>
      </header>

      {loadError && <div className="banner banner-error">{loadError}</div>}
      {actionError && <div className="banner banner-error">{actionError}</div>}

      <FilterBar
        statusCounts={statusCounts}
        selectedStatuses={selectedStatuses}
        onToggleStatus={toggleStatus}
        confidence={confidence}
        onConfidenceChange={setConfidence}
        providers={providers}
        provider={provider}
        onProviderChange={setProvider}
        search={search}
        onSearchChange={setSearch}
      />

      {loading ? (
        <p className="status-message">Loading…</p>
      ) : (
        <ImportedQuotesTable
          quotes={filteredQuotes}
          busyId={busyId}
          onApprove={setApproveTarget}
          onReject={(quote) => runStatusAction(quote, 'rejected')}
          onMarkDuplicate={(quote) => runStatusAction(quote, 'duplicate')}
          onResetToPending={(quote) => runStatusAction(quote, 'pending')}
        />
      )}

      {approveTarget && (
        <ApproveDialog
          quote={approveTarget}
          authors={authors}
          sources={sources}
          submitting={approveSubmitting}
          error={approveError}
          onCancel={() => {
            setApproveTarget(null)
            setApproveError(null)
          }}
          onSubmit={handleApproveSubmit}
        />
      )}
    </div>
  )
}

export default App
