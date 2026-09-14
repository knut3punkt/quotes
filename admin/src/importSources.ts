import {
  enrichAuthorsFromWikidata,
  importBhagavadGita,
  importBible,
  importDhammapada,
  importQuran,
  importTaoTeChing,
} from './api'
import type { StatEntry } from './components/ImportActionCard'
import type { AuthorEnrichmentResponse, ScriptureImportResult } from './types'

function toScriptureStats(result: ScriptureImportResult): StatEntry[] {
  return [
    { label: 'Inserted', value: result.quotesInserted },
    { label: 'Skipped as duplicate', value: result.quotesSkippedAsDuplicate },
    { label: 'Failed to fetch', value: result.quotesFailedToFetch },
  ]
}

function toEnrichmentStats(result: AuthorEnrichmentResponse): StatEntry[] {
  return [
    { label: 'Checked', value: result.checked },
    { label: 'Enriched', value: result.enriched },
    { label: 'Skipped', value: result.skipped },
  ]
}

export interface QuickImportSource {
  key: string
  title: string
  description: string
  run: () => Promise<StatEntry[]>
}

export const scriptureImportSources: QuickImportSource[] = [
  {
    key: 'tao-te-ching',
    title: 'Tao Te Ching',
    description: 'Imports all 81 chapters of the public-domain Legge translation. Bundled locally, no network calls.',
    run: () => importTaoTeChing().then(toScriptureStats),
  },
  {
    key: 'bhagavad-gita',
    title: 'Bhagavad Gita',
    description:
      'Imports verses that have a public-domain Shri Purohit Swami translation, fetched live from vedicscriptures.github.io.',
    run: () => importBhagavadGita().then(toScriptureStats),
  },
  {
    key: 'dhammapada',
    title: 'Dhammapada',
    description: "Imports all verses using Bhikkhu Sujato's CC0 translation, fetched live from SuttaCentral.",
    run: () => importDhammapada().then(toScriptureStats),
  },
  {
    key: 'bible-kjv',
    title: 'Bible (KJV)',
    description: 'Imports a hand-curated list of well-known KJV references, fetched live from bible-api.com.',
    run: () => importBible().then(toScriptureStats),
  },
  {
    key: 'quran-pickthall',
    title: 'Quran (Pickthall)',
    description: 'Imports a hand-curated list of references, fetched live from alquran.cloud.',
    run: () => importQuran().then(toScriptureStats),
  },
]

export const authorEnrichmentSource: QuickImportSource = {
  key: 'wikidata-enrich',
  title: 'Author metadata (Wikidata)',
  description: 'Backfills birth/death years for authors missing Wikidata data. Never overwrites existing values.',
  run: () => enrichAuthorsFromWikidata().then(toEnrichmentStats),
}
