import type { QuoteTag, TagFacet } from './types'

export const TAG_FACETS: TagFacet[] = ['concept', 'mood', 'motif']

export const FACET_LABELS: Record<TagFacet, string> = {
  concept: 'Concept',
  mood: 'Mood',
  motif: 'Motif',
}

const RELEVANCE_LABELS: Record<number, string> = { 1: 'peripheral', 2: 'significant', 3: 'central' }

export function facetChipClassName(facet: TagFacet): string {
  switch (facet) {
    case 'concept':
      return 'border-primary/30 bg-brand-tint text-primary'
    case 'mood':
      return 'border-warning/30 bg-warning/10 text-warning'
    case 'motif':
      return 'border-success/30 bg-success/10 text-success'
  }
}

/** Broad concepts are bold; tags that only come from an interpretation get a dashed, lighter chip. */
export function tagChipClassName(tag: QuoteTag): string {
  return [
    facetChipClassName(tag.facet),
    tag.breadth === 'broad' ? 'font-semibold' : 'font-normal',
    tag.basis === 'interpretation' ? 'border-dashed opacity-75' : '',
  ].join(' ')
}

export function tagChipTitle(tag: QuoteTag): string {
  return [
    FACET_LABELS[tag.facet].toLowerCase() + (tag.breadth ? ` (${tag.breadth})` : ''),
    RELEVANCE_LABELS[tag.relevance] ?? `relevance ${tag.relevance}`,
    tag.basis === 'interpretation' ? 'from an interpretation' : 'from the text',
    tag.origin === 'admin' ? 'added by admin' : 'generated',
  ].join(' · ')
}
