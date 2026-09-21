import type { PublicQuote } from '../types'

interface QuoteDisplayProps {
  quote: PublicQuote
}

export function QuoteDisplay({ quote }: QuoteDisplayProps) {
  return (
    <div className="flex h-full w-full flex-col items-center justify-center gap-8 px-24 text-center">
      <p className="max-w-5xl text-2xl leading-relaxed font-medium text-neutral-100">“{quote.text}”</p>
      {(quote.author || quote.sourceTitle) && (
        <div className="flex flex-col items-center gap-1 text-xl text-neutral-400">
          {quote.author && <p>— {quote.author}</p>}
          {quote.sourceTitle && (
            <p>
              {quote.sourceTitle}
              {quote.sourceDetail && `, ${quote.sourceDetail}`}
            </p>
          )}
        </div>
      )}
    </div>
  )
}
