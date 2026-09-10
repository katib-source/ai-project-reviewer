import { apiClient } from './apiClient'
import { API_ENDPOINTS } from './apiEndpoints'

export interface HealthStatus {
  status: 'ok'
}

export function getHealthStatus() {
  return apiClient<HealthStatus>(API_ENDPOINTS.health)
}
