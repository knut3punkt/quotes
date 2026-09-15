import { authorEnrichmentSource, scriptureImportSources } from '../importSources'
import { ImportActionCard } from './ImportActionCard'

export function QuickImportsSection() {
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h2 className="mb-3 text-[15px] font-medium">Quick imports</h2>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {scriptureImportSources.map((source) => (
            <ImportActionCard key={source.key} title={source.title} description={source.description} run={source.run} />
          ))}
        </div>
      </div>

      <div>
        <h2 className="mb-3 text-[15px] font-medium">Author enrichment</h2>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <ImportActionCard
            key={authorEnrichmentSource.key}
            title={authorEnrichmentSource.title}
            description={authorEnrichmentSource.description}
            run={authorEnrichmentSource.run}
          />
        </div>
      </div>
    </div>
  )
}
