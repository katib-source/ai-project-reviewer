import { apiClient } from './apiClient'
import { API_ENDPOINTS } from './apiEndpoints'

export interface ProjectTreeNode {
  name: string
  type: 'directory' | 'file'
  sizeInBytes: number
  children: ProjectTreeNode[]
}

export interface ImportedProject {
  projectId: string
  tree: ProjectTreeNode
}

export async function importProject(path: string): Promise<ImportedProject> {
  return apiClient<ImportedProject>(API_ENDPOINTS.projects.import, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ path }),
  })
}