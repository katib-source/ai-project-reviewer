interface MetricCardProps {
  label: string
  value: string
  detail: string
  tone?: 'mint' | 'gold' | 'coral'
}

export function MetricCard({ label, value, detail, tone = 'mint' }: MetricCardProps) {
  return (
    <article className={`metric-card ${tone}`}>
      <p>{label}</p>
      <strong>{value}</strong>
      <span>{detail}</span>
    </article>
  )
}
