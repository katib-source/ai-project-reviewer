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

/** Folders that are never worth uploading: dependencies, VCS data and build output. */
const SKIPPED_FOLDERS = new Set(['node_modules', '.git', 'target', 'dist', 'build', '.venv', '__pycache__', '.idea'])

/** The files from a folder picker that are worth sending, i.e. outside the skipped folders. */
export function selectUploadableFiles(files: FileList | File[]): File[] {
  return Array.from(files).filter((file) => {
    const folders = (file.webkitRelativePath || file.name).split('/').slice(0, -1)
    return !folders.some((folder) => SKIPPED_FOLDERS.has(folder))
  })
}

/**
 * Uploads a folder chosen with the browser's folder picker. Browsers never expose the folder's
 * real path, so the files themselves are sent; each part's filename carries its relative path.
 */
export async function uploadProjectFolder(files: File[]): Promise<ImportedProject> {
  const form = new FormData()
  for (const file of files) {
    form.append('files', file, file.webkitRelativePath || file.name)
  }
  // No Content-Type header: the browser sets the multipart boundary itself.
  return apiClient<ImportedProject>(API_ENDPOINTS.projects.upload, { method: 'POST', body: form })
}

export async function importProject(path: string): Promise<ImportedProject> {
  return apiClient<ImportedProject>(API_ENDPOINTS.projects.import, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ path }),
  })
}