import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../../../shared/stores/authStore'
import * as feedApi from '../api'
import type { Post } from '../types'
import { PostCard } from './PostCard'
import { renderWithProviders } from '../../../test/renderWithProviders'

const testPost: Post = {
  id: 'post-1',
  authorId: 'author-1',
  authorUsername: 'author',
  authorAvatarUrl: null,
  title: 'Тестовая публикация',
  content: null,
  coverImageUrl: null,
  imageUrls: [],
  status: 'PUBLISHED',
  publishedAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
  viewsCount: 10,
  upvotesCount: 5,
  commentsCount: 2,
  tags: [],
  keywords: [],
  version: 1,
}

describe('PostCard', () => {
  it('отправляет идентификатор публикации при оценке', async () => {
    useAuthStore.setState({
      token: 'token',
      user: { id: 'user-1', email: 'user@mail.ru', role: 'BASIC' },
    })

    const upvoteMock = vi.fn().mockResolvedValue({ upvoted: true, upvotesCount: 6 })
    vi.spyOn(feedApi, 'toggleUpvote').mockImplementation(upvoteMock)

    renderWithProviders(<PostCard post={testPost} />)
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: '♥ 5' }))

    await waitFor(() => {
      expect(upvoteMock.mock.calls[0]?.[0]).toBe('post-1')
    })
  })
})
