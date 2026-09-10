export type FindingSeverity = 'critical' | 'warning' | 'info'

export interface ProjectFile {
  path: string
  kind: 'folder' | 'file'
  status?: 'included' | 'excluded'
  depth: number
}

export interface Finding {
  id: string
  title: string
  description: string
  severity: FindingSeverity
  source: string
  score: number
}

export interface ReviewProject {
  name: string
  source: string
  files: ProjectFile[]
  findings: Finding[]
  coverage: number
  score: number
}

const currentProject: ReviewProject = {
  name: 'billing-service',
  source: 'github.com/acme/billing-service',
  coverage: 92,
  score: 78,
  files: [
    { path: 'billing-service', kind: 'folder', depth: 0 },
    { path: 'src', kind: 'folder', depth: 1 },
    { path: 'main', kind: 'folder', depth: 2 },
    { path: 'java', kind: 'folder', depth: 3 },
    { path: 'PaymentProcessor.java', kind: 'file', depth: 4, status: 'included' },
    { path: 'InvoiceService.java', kind: 'file', depth: 4, status: 'included' },
    { path: 'test', kind: 'folder', depth: 2 },
    { path: 'Dockerfile', kind: 'file', depth: 1, status: 'excluded' },
  ],
  findings: [
    {
      id: 'AR-102',
      title: 'Payment orchestration has competing responsibilities',
      description: 'PaymentProcessor coordinates validation, gateway calls, and persistence.',
      severity: 'critical',
      source: 'Architecture analysis',
      score: 42,
    },
    {
      id: 'DP-041',
      title: 'Gateway selection could use a Strategy boundary',
      description: 'Provider-specific branching makes the payment flow harder to extend.',
      severity: 'warning',
      source: 'Design pattern review',
      score: 71,
    },
    {
      id: 'QL-018',
      title: 'Invoice creation has strong test coverage',
      description: 'Relevant test cases cover successful and failed payment scenarios.',
      severity: 'info',
      source: 'Quality analysis',
      score: 91,
    },
  ],
}

export const reviewService = {
  getCurrentProject: async (): Promise<ReviewProject> => currentProject,
  startAnalysis: async (): Promise<void> => undefined,
  createReport: (project: ReviewProject): string => `\\documentclass{article}
\\title{AI Project Reviewer: ${project.name}}
\\date{\\today}
\\begin{document}
\\maketitle
\\section*{Overview}
Project score: ${project.score}/100. Analysis coverage: ${project.coverage}\\%.
\\section*{Findings}
${project.findings.map((finding) => `\\subsection*{${finding.id}: ${finding.title}}\n${finding.description}\n`).join('\n')}
\\end{document}
`,
}
