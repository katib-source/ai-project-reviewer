export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api'

export const API_ENDPOINTS = {
  health: '/health',
  projects: {
    import: '/projects/import',
  },
  reports: {
    download: '/reports/download',
  },
} as const