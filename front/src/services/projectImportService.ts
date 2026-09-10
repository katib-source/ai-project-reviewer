import { apiClient } from './apiClient'
import { API_ENDPOINTS } from './apiEndpoints'

export interface ImportedProject {
  analysisId: string
  projectName: string
}

export async function importProjectArchive(
  archive: File,
  includeTests: boolean,
  sandboxEnabled: boolean,
): Promise<ImportedProject> {
  const formData = new FormData()
  formData.append('archive', archive)
  formData.append('includeTests', String(includeTests))
  formData.append('sandboxEnabled', String(sandboxEnabled))

  return apiClient<ImportedProject>(API_ENDPOINTS.projects.import, {
    method: 'POST',
    body: formData,
  })
}