export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

export const API_ENDPOINTS = {
  criteria: '/api/criteria',
  projects: {
    import: '/api/projects',
  },
  analyses: {
    list: '/api/analyses',
    start: '/api/analyses',
    result: (analysisId: string) => `/api/analyses/${analysisId}`,
    events: (analysisId: string) => `/api/analyses/${analysisId}/events`,
    report: (analysisId: string) => `/api/analyses/${analysisId}/report`,
  },
  reports: {
    download: (fileName: string) => `/reports/${fileName}`,
  },
} as const