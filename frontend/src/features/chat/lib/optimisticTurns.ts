import type { QueryClient, QueryKey } from '@tanstack/react-query'
import type { TurnsPageResponse, TurnResponse } from '../types'

export type CacheSnapshot = [QueryKey, TurnsPageResponse | undefined][]

export interface AppendResult {
  snapshots: CacheSnapshot
  optimisticId: string
}

/**
 * Append an optimistic USER turn to every matching turns-cache entry.
 *
 * Uses getQueriesData with a prefix key so it matches the real cache keys
 * produced by useTurns (which include page, size, and auth-mode segments).
 */
export function appendOptimisticTurn(
  queryClient: QueryClient,
  roomId: string,
  text: string,
  participantId?: string | null,
): AppendResult {
  const prefixKey = ['chat', 'turns', roomId]
  const entries = queryClient.getQueriesData<TurnsPageResponse>({ queryKey: prefixKey })

  // Compute the highest seq across ALL matching cache entries first.
  let highestSeq = 0
  for (const [, data] of entries) {
    if (data?.items) {
      for (const t of data.items) {
        if (t.seq > highestSeq) highestSeq = t.seq
      }
    }
  }

  const optimisticId = `optimistic-${crypto.randomUUID()}`
  const optimisticTurn: TurnResponse = {
    id: optimisticId,
    roomId,
    seq: highestSeq + 1,
    role: 'USER',
    participantId: participantId ?? null,
    content: text,
    promptTokens: null,
    completionTokens: null,
    createdAt: new Date().toISOString(),
    optimistic: true,
  }

  // Save snapshots for rollback, then mutate each cache entry.
  const snapshots: CacheSnapshot = entries.map(([key, data]) => [key, data])

  for (const [key, data] of entries) {
    if (data?.items) {
      queryClient.setQueryData<TurnsPageResponse>(key, {
        ...data,
        items: [...data.items, optimisticTurn],
      })
    }
  }

  return { snapshots, optimisticId }
}

/**
 * Restore all cache entries from a previous snapshot (rollback).
 */
export function rollbackOptimisticTurn(
  queryClient: QueryClient,
  snapshots: CacheSnapshot,
): void {
  for (const [key, data] of snapshots) {
    queryClient.setQueryData(key, data)
  }
}
