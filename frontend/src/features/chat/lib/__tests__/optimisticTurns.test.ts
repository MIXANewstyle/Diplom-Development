import { describe, it, expect } from 'vitest'
import { QueryClient } from '@tanstack/react-query'
import { appendOptimisticTurn, rollbackOptimisticTurn } from '../optimisticTurns'
import type { TurnsPageResponse } from '../../types'

function makeQueryClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
}

describe('appendOptimisticTurn', () => {
  it('appends an optimistic turn to an existing cache entry under the full key', () => {
    const qc = makeQueryClient()
    const fullKey = ['chat', 'turns', 'r1', 0, 50, 'user']
    const seedData: TurnsPageResponse = {
      items: [
        {
          id: 't1',
          roomId: 'r1',
          seq: 3,
          role: 'USER',
          participantId: 'p1',
          content: 'hello',
          promptTokens: null,
          completionTokens: null,
          createdAt: '2026-01-01T00:00:00Z',
        },
      ],
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
    }
    qc.setQueryData(fullKey, seedData)

    const { snapshots, optimisticId } = appendOptimisticTurn(qc, 'r1', 'world')

    // The cache should now have 2 items
    const updated = qc.getQueryData<TurnsPageResponse>(fullKey)!
    expect(updated.items).toHaveLength(2)

    const last = updated.items[1]
    expect(last.seq).toBe(4)
    expect(last.role).toBe('USER')
    expect(last.content).toBe('world')
    expect(last.optimistic).toBe(true)
    expect(last.id).toBe(optimisticId)

    // Snapshots should contain the original data
    expect(snapshots).toHaveLength(1)
    expect(snapshots[0][1]).toBe(seedData)
  })

  it('rollback restores the original data', () => {
    const qc = makeQueryClient()
    const fullKey = ['chat', 'turns', 'r1', 0, 50, 'user']
    const seedData: TurnsPageResponse = {
      items: [
        {
          id: 't1',
          roomId: 'r1',
          seq: 3,
          role: 'USER',
          participantId: 'p1',
          content: 'hello',
          promptTokens: null,
          completionTokens: null,
          createdAt: '2026-01-01T00:00:00Z',
        },
      ],
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
    }
    qc.setQueryData(fullKey, seedData)

    const { snapshots } = appendOptimisticTurn(qc, 'r1', 'world')

    // Verify mutation happened
    expect(qc.getQueryData<TurnsPageResponse>(fullKey)!.items).toHaveLength(2)

    // Rollback
    rollbackOptimisticTurn(qc, snapshots)

    const restored = qc.getQueryData<TurnsPageResponse>(fullKey)!
    expect(restored.items).toHaveLength(1)
    expect(restored).toBe(seedData) // exact same reference
  })

  it('handles empty cache gracefully (no entries matched)', () => {
    const qc = makeQueryClient()

    // No cache entries for 'r1' at all
    const { snapshots, optimisticId } = appendOptimisticTurn(qc, 'r1', 'text')

    expect(snapshots).toHaveLength(0)
    expect(optimisticId).toMatch(/^optimistic-/)
  })
})
