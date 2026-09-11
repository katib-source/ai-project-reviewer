import { apiClient } from './apiClient'
import { API_BASE_URL, API_ENDPOINTS } from './apiEndpoints'
import type { ProjectTreeNode } from './projectImportService'

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
  id: string
  name: string
  source: string
  files: ProjectFile[]
  findings: Finding[]
  coverage: number
  score: number
}

export interface Criterion {
  id: string
  name: string
  description: string
  weight: number
}

export interface CriterionResult {
  criterionId: string
  criterionName: string
  successful: boolean
  score: number
  comment: string
}

export interface AnalysisResult {
  analysisId: string
  overallScore: number
  criteria: CriterionResult[]
}

export interface AnalysisHistoryItem {
  analysisId: string
  projectPath: string
  projectName: string
  overallScore: number
  completedAt: string
}

export interface ReportResponse {
  texUrl: string
  pdfUrl: string
}

export interface AnalysisProgress {
  criterionId?: string
  criterionName?: string
  successful?: boolean
  score?: number
  comment?: string
  overallScore?: number
  message?: string
}

export function flattenProjectTree(node: ProjectTreeNode, depth = 0, parentPath = ''): ProjectFile[] {
  const path = parentPath ? `${parentPath}/${node.name}` : node.name
  const files: ProjectFile[] = [{
    path,
    kind: node.type === 'directory' ? 'folder' : 'file',
    status: node.type === 'file' ? 'included' : undefined,
    depth,
  }]

  for (const child of node.children) {
    files.push(...flattenProjectTree(child, depth + 1, path))
  }
  return files
}

export function getCriteria() {
  return apiClient<Criterion[]>(API_ENDPOINTS.criteria)
}

export function startAnalysis(projectId: string, criterionIds: string[]) {
  return apiClient<{ analysisId: string }>(API_ENDPOINTS.analyses.start, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ projectId, criterionIds }),
  })
}

export function getAnalysis(analysisId: string) {
  return apiClient<AnalysisResult>(API_ENDPOINTS.analyses.result(analysisId))
}

export function getAnalysisHistory() {
  return apiClient<AnalysisHistoryItem[]>(API_ENDPOINTS.analyses.list)
}

export function generateReport(analysisId: string) {
  return apiClient<ReportResponse>(API_ENDPOINTS.analyses.report(analysisId), { method: 'POST' })
}

export function openAnalysisEvents(
  analysisId: string,
  handlers: {
    onStarted: (event: AnalysisProgress) => void
    onCompleted: (event: AnalysisProgress) => void
    onError: (event: AnalysisProgress) => void
  },
) {
  const eventSource = new EventSource(`${API_BASE_URL}${API_ENDPOINTS.analyses.events(analysisId)}`)
  const parse = (event: MessageEvent<string>) => {
    if (!event.data) return {}
    try {
      return JSON.parse(event.data) as AnalysisProgress
    } catch {
      return { message: 'The backend sent an invalid analysis event.' }
    }
  }
  eventSource.addEventListener('criterion-started', (event) => handlers.onStarted(parse(event as MessageEvent<string>)))
  eventSource.addEventListener('criterion-completed', (event) => handlers.onCompleted(parse(event as MessageEvent<string>)))
  eventSource.addEventListener('analysis-completed', (event) => handlers.onCompleted(parse(event as MessageEvent<string>)))
  eventSource.addEventListener('error', (event) => handlers.onError(parse(event as MessageEvent<string>)))
  return eventSource
}