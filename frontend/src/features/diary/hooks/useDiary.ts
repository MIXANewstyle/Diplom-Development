import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import {
  closeDay,
  deleteMemoryFact,
  generateAutoSummary,
  getCalendar,
  getDay,
  getMemories,
  listMemoryFacts,
  openDay,
  saveUserSummary,
  updateMemoryFact,
} from '../api'
import type { DiaryDayResponse, DiaryPeriodType } from '../types'
import { isWritableLocally } from '../lib/dates'

/** A closed day whose summary has not arrived yet (summarization is asynchronous on the server). */
export const isSummaryPending = (day: DiaryDayResponse | null | undefined): boolean => {
  if (!day || day.summary || day.status !== 'ARCHIVED' || day.turnCount === 0) return false
  if (!day.closedAt) return false
  const closedMs = new Date(day.closedAt).getTime()
  // give the pipeline two minutes; after that stop polling and show whatever we have
  return Number.isFinite(closedMs) && Date.now() - closedMs < 2 * 60 * 1000
}

export const diaryKeys = {
  calendar: (month: string) => ['diary', 'calendar', month] as const,
  day: (date: string) => ['diary', 'day', date] as const,
  memories: (date: string) => ['diary', 'memories', date] as const,
  facts: ['diary', 'facts'] as const,
}

export const useDiaryCalendar = (month: string) =>
  useQuery({
    queryKey: diaryKeys.calendar(month),
    queryFn: () => getCalendar(month),
  })

/**
 * Today/yesterday: PUT (get-or-create). Older days: GET (404 = no entry, surfaced as null).
 */
export const useDiaryDay = (date: string, valid: boolean) =>
  useQuery({
    queryKey: diaryKeys.day(date),
    enabled: valid,
    retry: false,
    refetchInterval: (query) => (isSummaryPending(query.state.data) ? 3000 : false),
    queryFn: async () => {
      if (isWritableLocally(date)) {
        try {
          return await openDay(date)
        } catch (error) {
          // the server's window is ±1 UTC day; if it disagrees, fall back to read-only
          if (isAxiosError(error) && error.response?.status === 409) {
            return getDay(date).catch(() => null)
          }
          throw error
        }
      }
      try {
        return await getDay(date)
      } catch (error) {
        if (isAxiosError(error) && error.response?.status === 404) return null
        throw error
      }
    },
  })

export const useCloseDay = (date: string) => {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => closeDay(date),
    onSuccess: (day) => {
      queryClient.setQueryData(diaryKeys.day(date), day)
      queryClient.invalidateQueries({ queryKey: ['diary', 'calendar'] })
      queryClient.invalidateQueries({ queryKey: ['chat', 'room', day.roomId] })
      // facts and the RAG index arrive a little later; refresh them when the summary shows up
      setTimeout(() => queryClient.invalidateQueries({ queryKey: diaryKeys.facts }), 15_000)
    },
  })
}

export const useDiaryMemories = (date: string, enabled: boolean) =>
  useQuery({
    queryKey: diaryKeys.memories(date),
    queryFn: () => getMemories(date),
    enabled,
  })

export const useSaveUserSummary = (month: string) => {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (v: { type: DiaryPeriodType; start: string; text: string }) =>
      saveUserSummary(v.type, v.start, v.text),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: diaryKeys.calendar(month) }),
  })
}

export const useGenerateAutoSummary = (month: string) => {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (v: { type: DiaryPeriodType; start: string }) => generateAutoSummary(v.type, v.start),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: diaryKeys.calendar(month) }),
  })
}

export const useMemoryFacts = () =>
  useQuery({ queryKey: diaryKeys.facts, queryFn: listMemoryFacts })

export const useUpdateMemoryFact = () => {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (v: { id: string; content: string }) => updateMemoryFact(v.id, v.content),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: diaryKeys.facts }),
  })
}

export const useDeleteMemoryFact = () => {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => deleteMemoryFact(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: diaryKeys.facts }),
  })
}
