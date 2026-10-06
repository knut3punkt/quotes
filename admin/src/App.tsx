import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { TooltipProvider } from '@/components/ui/tooltip'
import { ImportedQuotesPage } from './components/ImportedQuotesPage'
import { ImportPage } from './components/ImportPage'
import { QuotesPage } from './components/QuotesPage'
import { TagsPage } from './components/TagsPage'
import { Toaster } from './components/Toaster'

type AdminPage = 'review' | 'import' | 'quotes' | 'tags'

const PAGE_TITLES: Record<AdminPage, string> = {
  review: 'Imported quotes',
  import: 'Import quotes',
  quotes: 'Approved quotes',
  tags: 'Tags',
}

const PAGE_DESCRIPTIONS: Record<AdminPage, string> = {
  review: 'Review staged imports and promote them into the quote library.',
  import: 'Stage quotes into the review queue from Wikiquote, scripture sources, or refresh author metadata.',
  quotes: 'Browse the curated quotes that have been approved into the library.',
  tags: 'Rename, reclassify, and merge the tags generated for quotes, and generate their images.',
}

function App() {
  const [page, setPage] = useState<AdminPage>('review')

  return (
    <TooltipProvider>
      <div className="mx-auto max-w-[1280px] px-8 pt-6 pb-16">
        <Toaster />
        <header className="mb-6 flex flex-wrap items-start justify-between gap-4">
          <div>
            <h1 className="mb-1 text-[28px]">{PAGE_TITLES[page]}</h1>
            <p className="text-muted-foreground">{PAGE_DESCRIPTIONS[page]}</p>
          </div>
          <div className="flex gap-2">
            <Button type="button" variant={page === 'review' ? 'default' : 'outline'} onClick={() => setPage('review')}>
              Review imports
            </Button>
            <Button type="button" variant={page === 'import' ? 'default' : 'outline'} onClick={() => setPage('import')}>
              Import quotes
            </Button>
            <Button type="button" variant={page === 'quotes' ? 'default' : 'outline'} onClick={() => setPage('quotes')}>
              Approved quotes
            </Button>
            <Button type="button" variant={page === 'tags' ? 'default' : 'outline'} onClick={() => setPage('tags')}>
              Tags
            </Button>
          </div>
        </header>

        {page === 'review' && <ImportedQuotesPage />}

        {page === 'import' && <ImportPage />}

        {page === 'quotes' && <QuotesPage />}

        {page === 'tags' && <TagsPage />}
      </div>
    </TooltipProvider>
  )
}

export default App
