import { apiClient } from './apiClient'
import { API_ENDPOINTS } from './apiEndpoints'

export function getHealthStatus() {
  return apiClient<unknown>(API_ENDPOINTS.criteria)
}
