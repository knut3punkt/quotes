import { useEffect, useState } from 'react'
import { fetchRandomQuotes } from './api'
import { QuoteDisplay } from './components/QuoteDisplay'
import { useQuoteCarousel } from './hooks/use-quote-carousel'
import type { PublicQuote } from './types'

function App() {
  const [quotes, setQuotes] = useState<PublicQuote[]>([])
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')
  const { currentQuote, next, previous } = useQuoteCarousel(quotes)

  useEffect(() => {
    fetchRandomQuotes()
      .then((fetched) => {
        setQuotes(fetched)
        setStatus('ready')
      })
      .catch((err) => {
setStatus('error')
console.error(err)
      } )
  }, [])

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'ArrowRight') next()
      if (event.key === 'ArrowLeft') previous()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [next, previous])

  console.log(quotes)

  return (
    <div className="flex h-full w-full items-center justify-center bg-neutral-950 text-neutral-100">
      {status === 'loading' && <p className="text-xl text-neutral-400">Loading quotes…</p>}
      {status === 'error' && <p className="text-xl text-neutral-400">Couldn't load quotes.</p>}
      {status === 'ready' && currentQuote && <QuoteDisplay quote={currentQuote} />}
    </div>
  )
}

export default App
