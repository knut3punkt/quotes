import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Loader2 } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { fetchTags, mergeTag, updateTag } from '../api'
import { useDebouncedValue } from '../hooks/use-debounced-value'
import { toast } from '../hooks/use-toast'
import { FACET_LABELS, TAG_FACETS, facetChipClassName } from '../tagStyles'
import type { TagBreadth, TagFacet, TagSummary } from '../types'
import { ConfirmDialog } from './ConfirmDialog'
import { ImageGenerationPanel } from './ImageGenerationPanel'

const FILTER_DEBOUNCE_MILLIS = 250

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : 'Something went wrong'
}

interface MergeRequest {
  source: TagSummary
  target: TagSummary
}

/**
 * Keeps the open tag vocabulary tidy: rename a tag (also to fix casing), change a concept's breadth, or
 * merge a near-duplicate into another tag. A merged tag's name keeps resolving to its target, so the
 * model can't re-create it.
 */
export function TagsPage() {
  const [tags, setTags] = useState<TagSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [facet, setFacet] = useState<'all' | TagFacet>('all')
  const [search, setSearch] = useState('')
  const debouncedSearch = useDebouncedValue(search, FILTER_DEBOUNCE_MILLIS)

  const [editingId, setEditingId] = useState<number | null>(null)
  const [editName, setEditName] = useState('')
  const [savingId, setSavingId] = useState<number | null>(null)
  const [mergeTargets, setMergeTargets] = useState<Record<number, string>>({})
  const [mergeRequest, setMergeRequest] = useState<MergeRequest | null>(null)
  const [merging, setMerging] = useState(false)
  const [mergeError, setMergeError] = useState<string | null>(null)

  // The merge-target dropdown needs every tag of the facet, not just the filtered rows.
  const [allTags, setAllTags] = useState<TagSummary[]>([])

  const reload = useCallback(() => {
    setLoading(true)
    setLoadError(null)
    Promise.all([
      fetchTags({ facet: facet === 'all' ? undefined : facet, search: debouncedSearch.trim() || undefined }),
      fetchTags(),
    ])
      .then(([filtered, all]) => {
        setTags(filtered)
        setAllTags(all)
      })
      .catch((err) => setLoadError(errorMessage(err)))
      .finally(() => setLoading(false))
  }, [facet, debouncedSearch])

  useEffect(() => {
    reload()
  }, [reload])

  const tagsByFacet = useMemo(() => {
    const byFacet: Record<TagFacet, TagSummary[]> = { concept: [], mood: [], motif: [] }
    for (const tag of allTags) byFacet[tag.facet].push(tag)
    for (const list of Object.values(byFacet)) list.sort((a, b) => a.name.localeCompare(b.name))
    return byFacet
  }, [allTags])

  const replaceTag = (updated: TagSummary) => {
    setTags((prev) => prev.map((tag) => (tag.id === updated.id ? updated : tag)))
    setAllTags((prev) => prev.map((tag) => (tag.id === updated.id ? updated : tag)))
  }

  const saveName = async (tag: TagSummary) => {
    const name = editName.trim()
    if (!name || name === tag.name) {
      setEditingId(null)
      return
    }
    setSavingId(tag.id)
    try {
      replaceTag(await updateTag(tag.id, { name }))
      setEditingId(null)
    } catch (err) {
      toast.error('Could not rename tag', errorMessage(err))
    } finally {
      setSavingId(null)
    }
  }

  const saveBreadth = async (tag: TagSummary, breadth: TagBreadth) => {
    setSavingId(tag.id)
    try {
      replaceTag(await updateTag(tag.id, { breadth }))
    } catch (err) {
      toast.error('Could not change breadth', errorMessage(err))
    } finally {
      setSavingId(null)
    }
  }

  const requestMerge = (source: TagSummary) => {
    const target = allTags.find((tag) => String(tag.id) === mergeTargets[source.id])
    if (!target) return
    setMergeError(null)
    setMergeRequest({ source, target })
  }

  const confirmMerge = async () => {
    if (!mergeRequest) return
    setMerging(true)
    setMergeError(null)
    try {
      await mergeTag(mergeRequest.source.id, mergeRequest.target.id)
      toast.success(`Merged “${mergeRequest.source.name}” into “${mergeRequest.target.name}”`)
      setMergeRequest(null)
      reload()
    } catch (err) {
      setMergeError(errorMessage(err))
    } finally {
      setMerging(false)
    }
  }

  return (
    <div>
      <ImageGenerationPanel />

      {loadError && (
        <Alert variant="destructive" className="mb-4 border-destructive/30 bg-destructive/10">
          <AlertDescription>{loadError}</AlertDescription>
        </Alert>
      )}

      <div className="mb-5 flex flex-wrap gap-4 border-b border-border pb-4">
        <div className="flex flex-col gap-1">
          <Label htmlFor="tags-search" className="text-xs font-normal text-muted-foreground">
            Search
          </Label>
          <Input
            id="tags-search"
            type="search"
            value={search}
            placeholder="Tag name…"
            onChange={(event) => setSearch(event.target.value)}
            className="min-w-[220px]"
          />
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="tags-facet" className="text-xs font-normal text-muted-foreground">
            Facet
          </Label>
          <Select value={facet} onValueChange={(value) => setFacet(value as 'all' | TagFacet)}>
            <SelectTrigger id="tags-facet" size="sm" className="min-w-[140px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All</SelectItem>
              {TAG_FACETS.map((option) => (
                <SelectItem key={option} value={option}>
                  {FACET_LABELS[option]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      {mergeRequest && (
        <ConfirmDialog
          title="Merge tags"
          description={`Every quote tagged “${mergeRequest.source.name}” (${mergeRequest.source.usageCount}) moves to “${mergeRequest.target.name}”, and “${mergeRequest.source.name}” becomes an alias of it. This can't be undone from the admin.`}
          confirmLabel="Merge"
          submittingLabel="Merging…"
          submitting={merging}
          error={mergeError}
          onCancel={() => !merging && setMergeRequest(null)}
          onConfirm={confirmMerge}
        />
      )}

      {loading ? (
        <p className="py-8 text-center text-muted-foreground">Loading…</p>
      ) : tags.length === 0 ? (
        <p className="py-8 text-center text-muted-foreground">No tags match these filters.</p>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Tag</TableHead>
              <TableHead>Facet</TableHead>
              <TableHead>Breadth</TableHead>
              <TableHead className="text-right">Uses</TableHead>
              <TableHead>Aliases</TableHead>
              <TableHead>Merge into</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {tags.map((tag) => (
              <TableRow key={tag.id}>
                <TableCell className="align-top">
                  {editingId === tag.id ? (
                    <form
                      className="flex items-center gap-1"
                      onSubmit={(event) => {
                        event.preventDefault()
                        void saveName(tag)
                      }}
                    >
                      <Input
                        className="h-7 w-48 text-sm"
                        value={editName}
                        autoFocus
                        onChange={(event) => setEditName(event.target.value)}
                        aria-label={`New name for ${tag.name}`}
                      />
                      <Button type="submit" size="sm" variant="outline" className="h-7" disabled={savingId === tag.id}>
                        {savingId === tag.id && <Loader2 className="animate-spin" />}
                        Save
                      </Button>
                      <Button type="button" size="sm" variant="ghost" className="h-7" onClick={() => setEditingId(null)}>
                        Cancel
                      </Button>
                    </form>
                  ) : (
                    <button
                      type="button"
                      className="text-left hover:underline"
                      title="Rename"
                      onClick={() => {
                        setEditingId(tag.id)
                        setEditName(tag.name)
                      }}
                    >
                      {tag.name}
                    </button>
                  )}
                </TableCell>
                <TableCell className="align-top">
                  <span className={`rounded-full border px-2 py-0.5 text-xs ${facetChipClassName(tag.facet)}`}>
                    {FACET_LABELS[tag.facet]}
                  </span>
                </TableCell>
                <TableCell className="align-top">
                  {tag.facet === 'concept' ? (
                    <select
                      className="h-7 rounded-md border border-input bg-background px-1 text-xs"
                      value={tag.breadth ?? 'specific'}
                      disabled={savingId === tag.id}
                      onChange={(event) => void saveBreadth(tag, event.target.value as TagBreadth)}
                      aria-label={`Breadth of ${tag.name}`}
                    >
                      <option value="broad">Broad</option>
                      <option value="specific">Specific</option>
                    </select>
                  ) : (
                    <span className="text-muted-foreground">—</span>
                  )}
                </TableCell>
                <TableCell className="text-right align-top tabular-nums">{tag.usageCount}</TableCell>
                <TableCell className="max-w-[240px] align-top whitespace-normal text-xs text-muted-foreground">
                  {tag.aliases.length > 0 ? tag.aliases.join(', ') : '—'}
                </TableCell>
                <TableCell className="align-top">
                  <div className="flex items-center gap-1">
                    <select
                      className="h-7 max-w-[180px] rounded-md border border-input bg-background px-1 text-xs"
                      value={mergeTargets[tag.id] ?? ''}
                      onChange={(event) => setMergeTargets((prev) => ({ ...prev, [tag.id]: event.target.value }))}
                      aria-label={`Tag to merge ${tag.name} into`}
                    >
                      <option value="">Choose tag…</option>
                      {tagsByFacet[tag.facet]
                        .filter((option) => option.id !== tag.id)
                        .map((option) => (
                          <option key={option.id} value={String(option.id)}>
                            {option.name} ({option.usageCount})
                          </option>
                        ))}
                    </select>
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      className="h-7"
                      disabled={!mergeTargets[tag.id]}
                      onClick={() => requestMerge(tag)}
                    >
                      Merge
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <p className="mt-4 text-sm text-muted-foreground">{tags.length} tags</p>
    </div>
  )
}
