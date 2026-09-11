import { ApiError, apiClient } from './apiClient'
import { API_BASE_URL, API_ENDPOINTS } from './apiEndpoints'

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

/**
 * Mirrors the backend: MAX_UPLOAD_FILES / MAX_UPLOAD_BYTES in Main, and the default
 * PROJECT_MAX_FILE_SIZE_BYTES above which the importer ignores a file anyway.
 */
const UPLOAD_LIMITS = { maxFiles: 5_000, maxTotalBytes: 100_000_000, maxFileBytes: 1_000_000 }

/**
 * The files from a folder picker that are worth sending: outside the skipped folders and small
 * enough for the backend to analyze (so archives, videos and the like never travel).
 */
export function selectUploadableFiles(files: File[]): File[] {
  return files.filter((file) => {
    const folders = (file.webkitRelativePath || file.name).split('/').slice(0, -1)
    return file.size <= UPLOAD_LIMITS.maxFileBytes && !folders.some((folder) => SKIPPED_FOLDERS.has(folder))
  })
}

/** Why these files cannot be uploaded, or null if they fit the backend's limits. */
export function uploadLimitProblem(files: File[]): string | null {
  if (files.length > UPLOAD_LIMITS.maxFiles) {
    return `This folder still has ${files.length.toLocaleString()} files after skipping dependency and build folders; `
      + `the limit is ${UPLOAD_LIMITS.maxFiles.toLocaleString()}. Choose the project folder itself, not a folder containing several projects.`
  }
  const totalBytes = files.reduce((sum, file) => sum + file.size, 0)
  if (totalBytes > UPLOAD_LIMITS.maxTotalBytes) {
    return `This folder is ${Math.round(totalBytes / 1_000_000)} MB; the limit is ${UPLOAD_LIMITS.maxTotalBytes / 1_000_000} MB.`
  }
  return null
}

/**
 * Uploads a folder chosen with the browser's folder picker. Browsers never expose the folder's
 * real path, so the files themselves are sent; each part's filename carries its relative path.
 * Uses XMLHttpRequest rather than fetch because only XHR reports upload progress.
 */
export function uploadProjectFolder(files: File[], onProgress?: (fraction: number) => void): Promise<ImportedProject> {
  const form = new FormData()
  for (const file of files) {
    form.append('files', file, file.webkitRelativePath || file.name)
  }
  return new Promise((resolve, reject) => {
    const request = new XMLHttpRequest()
    request.open('POST', `${API_BASE_URL}${API_ENDPOINTS.projects.upload}`)
    request.setRequestHeader('Accept', 'application/json')
    request.responseType = 'json'
    request.upload.onprogress = (event) => {
      if (event.lengthComputable) onProgress?.(event.loaded / event.total)
    }
    request.onload = () => {
      if (request.status >= 200 && request.status < 300) {
        resolve(request.response as ImportedProject)
      } else {
        const body = request.response as { message?: string } | null
        reject(new ApiError(body?.message ?? `Upload failed with status ${request.status}.`, request.status))
      }
    }
    request.onerror = () => reject(new ApiError('Upload failed: the backend could not be reached.', 0))
    // No Content-Type header: the browser sets the multipart boundary itself.
    request.send(form)
  })
}

export async function importProject(path: string): Promise<ImportedProject> {
  return apiClient<ImportedProject>(API_ENDPOINTS.projects.import, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ path }),
  })
}