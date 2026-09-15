import { useState } from 'react'
import { Loader2 } from 'lucide-react'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Progress } from '@/components/ui/progress'
import { toast } from '../hooks/use-toast'

export interface StatEntry {
  label: string
  value: number
}

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : 'Something went wrong'
}

function formatStats(stats: StatEntry[]): string {
  return stats.map((stat) => `${stat.label}: ${stat.value}`).join(', ')
}

interface ImportActionCardProps {
  title: string
  description: string
  run: () => Promise<StatEntry[]>
}

export function ImportActionCard({ title, description, run }: ImportActionCardProps) {
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [stats, setStats] = useState<StatEntry[] | null>(null)

  const handleRun = async () => {
    setSubmitting(true)
    setError(null)
    try {
      const result = await run()
      setStats(result)
      toast.success(`${title} complete`, formatStats(result))
    } catch (err) {
      const message = errorMessage(err)
      setError(message)
      toast.error(`${title} failed`, message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex flex-col gap-3 rounded-lg border border-border bg-card p-4">
      <div>
        <h3 className="text-[15px] font-medium">{title}</h3>
        <p className="mt-1 text-[13px] text-muted-foreground">{description}</p>
      </div>

      <div className="flex flex-col gap-2">
        <div>
          <Button type="button" variant="outline" size="sm" disabled={submitting} onClick={handleRun}>
            {submitting && <Loader2 className="animate-spin" />}
            {submitting ? 'Running…' : 'Run'}
          </Button>
        </div>
        {submitting && <Progress value={null} />}
      </div>

      {error && (
        <Alert variant="destructive" className="border-destructive/30 bg-destructive/10">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {stats && (
        <dl className="grid grid-cols-3 gap-2 border-t border-border pt-3">
          {stats.map((stat) => (
            <div key={stat.label}>
              <dt className="text-[11px] text-muted-foreground">{stat.label}</dt>
              <dd className="text-[15px] font-medium">{stat.value}</dd>
            </div>
          ))}
        </dl>
      )}
    </div>
  )
}
