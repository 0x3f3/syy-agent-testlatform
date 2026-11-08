const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, options)
  const text = await response.text()
  let payload = text
  try { payload = text ? JSON.parse(text) : null } catch { /* plain text response */ }
  if (!response.ok) {
    const message = payload && typeof payload === 'object' ? (payload.message || payload.error) : payload
    throw new Error(message || `请求失败（HTTP ${response.status}）`)
  }
  return payload
}

export const checkHealth = () => request('/health')

export async function uploadOpenApi(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request('/test/swagger/upload', { method: 'POST', body: formData })
}

export const getDocuments = () => request('/test/documents')
export const getApis = (documentId) => request(`/test/apis?documentId=${encodeURIComponent(documentId)}`)
export const getCases = (documentId) => request(`/test/cases?documentId=${encodeURIComponent(documentId)}`)
export const getKnowledgeStatus = () => request('/test/knowledge/status')
export const generateCases = (documentId) => request(`/test/documents/${documentId}/generate-cases`, { method: 'POST' })
export const getScenarioCoverage = (documentId) => request(`/test/documents/${documentId}/coverage`)
export const getTasks = () => request('/test/tasks')
export const getTaskExecutions = (taskId) => request(`/test/tasks/${taskId}/executions`)
export const createTestTask = (payload) => request('/test/tasks', {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
})
export const getReportSummary = () => request('/test/report/summary')
export const getReportTrend = () => request('/test/report/trend')
export const getReportQuality = () => request('/test/report/quality')

export const evaluateTestResult = (result) => request('/test-agent/evaluate', {
  method: 'POST', headers: { 'Content-Type': 'text/plain;charset=UTF-8' }, body: result
})
export const getTask = (taskId) => request(`/test/tasks/${taskId}`)
export const getTaskProgress = (taskId) => request(`/test/tasks/${taskId}/progress`)
export const getTaskReport = (taskId) => request(`/test/tasks/${taskId}/report`)
export const getSchedules = () => request('/test/schedules')
export const createSchedule = (payload) => request('/test/schedules', {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
})
export const setScheduleEnabled = (id, enabled) => request(`/test/schedules/${id}/enabled`, {
  method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ enabled })
})
export const deleteSchedule = (id) => request(`/test/schedules/${id}`, { method: 'DELETE' })
