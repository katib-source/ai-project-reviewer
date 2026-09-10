import { ArrowUpRight } from 'lucide-react'
import type { Finding } from '../services/reviewService'

interface FindingsTableProps {
  findings: Finding[]
  onSelect: (finding: Finding) => void
}

export function FindingsTable({ findings, onSelect }: FindingsTableProps) {
  return (
    <section className="panel findings" aria-labelledby="findings-title">
      <div className="panel-heading"><div><p className="eyebrow">Latest evaluation</p><h2 id="findings-title">Findings</h2></div><button className="text-button" type="button">View all</button></div>
      <div className="findings-list">
        {findings.map((finding) => (
          <button className="finding-row" key={finding.id} onClick={() => onSelect(finding)} type="button">
            <span className={`severity ${finding.severity}`}>{finding.severity}</span>
            <span className="finding-content"><strong>{finding.title}</strong><small>{finding.source} · {finding.id}</small></span>
            <span className="finding-score">{finding.score}<small>/100</small></span>
            <ArrowUpRight size={17} />
          </button>
        ))}
      </div>
    </section>
  )
}
