import { apiClient } from '../../shared/api/client'
import type {
  DiaryCalendarResponse,
  DiaryDayResponse,
  DiaryMemoriesResponse,
  DiaryPeriodResponse,
  DiaryPeriodType,
  MemoryFactResponse,
} from './types'

export const getCalendar = async (month: string): Promise<DiaryCalendarResponse> => {
  const { data } = await apiClient.get<DiaryCalendarResponse>('/api/v1/diary/calendar', {
    params: { month },
  })
  return data
}

/** Get-or-create the day room; only today/yesterday are accepted by the server (409 otherwise). */
export const openDay = async (date: string): Promise<DiaryDayResponse> => {
  const { data } = await apiClient.put<DiaryDayResponse>(`/api/v1/diary/days/${date}`)
  return data
}

export const getDay = async (date: string): Promise<DiaryDayResponse> => {
  const { data } = await apiClient.get<DiaryDayResponse>(`/api/v1/diary/days/${date}`)
  return data
}

export const deleteDay = async (date: string): Promise<void> => {
  await apiClient.delete(`/api/v1/diary/days/${date}`)
}

export const getMemories = async (date: string): Promise<DiaryMemoriesResponse> => {
  const { data } = await apiClient.get<DiaryMemoriesResponse>('/api/v1/diary/memories', {
    params: { date },
  })
  return data
}

export const getPeriod = async (type: DiaryPeriodType, start: string): Promise<DiaryPeriodResponse> => {
  const { data } = await apiClient.get<DiaryPeriodResponse>('/api/v1/diary/periods', {
    params: { type, start },
  })
  return data
}

export const saveUserSummary = async (
  type: DiaryPeriodType,
  start: string,
  text: string,
): Promise<DiaryPeriodResponse> => {
  const { data } = await apiClient.put<DiaryPeriodResponse>(
    `/api/v1/diary/periods/${type}/${start}/user-summary`,
    { text },
  )
  return data
}

export const generateAutoSummary = async (
  type: DiaryPeriodType,
  start: string,
): Promise<DiaryPeriodResponse> => {
  const { data } = await apiClient.post<DiaryPeriodResponse>(
    `/api/v1/diary/periods/${type}/${start}/auto-summary`,
  )
  return data
}

export const listMemoryFacts = async (): Promise<MemoryFactResponse[]> => {
  const { data } = await apiClient.get<MemoryFactResponse[]>('/api/v1/diary/memory-facts')
  return data
}

export const updateMemoryFact = async (id: string, content: string): Promise<MemoryFactResponse> => {
  const { data } = await apiClient.patch<MemoryFactResponse>(`/api/v1/diary/memory-facts/${id}`, { content })
  return data
}

export const deleteMemoryFact = async (id: string): Promise<void> => {
  await apiClient.delete(`/api/v1/diary/memory-facts/${id}`)
}
