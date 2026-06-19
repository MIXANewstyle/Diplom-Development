import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import * as socialApi from '../features/social/api'
import { FriendsPage } from './FriendsPage'
import { renderWithProviders } from '../test/renderWithProviders'

const emptyFriends = {
  friends: [],
  incomingRequests: [],
  outgoingRequests: [],
}

describe('FriendsPage', () => {
  it('принимает входящую заявку в друзья', async () => {
    vi.spyOn(socialApi, 'getMyFriends').mockResolvedValue({
      ...emptyFriends,
      incomingRequests: [{ id: 'user-2', username: 'alice', avatarUrl: null }],
    })
    const acceptMock = vi.fn().mockResolvedValue(undefined)
    vi.spyOn(socialApi, 'acceptFriendRequest').mockImplementation(acceptMock)

    renderWithProviders(<FriendsPage />)
    const user = userEvent.setup()

    await waitFor(() => {
      expect(screen.getByText('alice')).toBeInTheDocument()
    })

    await user.click(screen.getByRole('button', { name: 'Принять' }))

    await waitFor(() => {
      expect(acceptMock.mock.calls[0]?.[0]).toBe('user-2')
    })
  })

  it('отправляет заявку в друзья найденному пользователю', async () => {
    vi.spyOn(socialApi, 'getMyFriends').mockResolvedValue(emptyFriends)
    vi.spyOn(socialApi, 'searchUsers').mockResolvedValue([
      { id: 'user-3', username: 'bob', avatarUrl: null },
    ])
    const sendMock = vi.fn().mockResolvedValue(undefined)
    vi.spyOn(socialApi, 'sendFriendRequest').mockImplementation(sendMock)

    renderWithProviders(<FriendsPage />)
    const user = userEvent.setup()

    await waitFor(() => {
      expect(screen.queryByText('Загрузка...')).not.toBeInTheDocument()
    })

    await user.type(screen.getByPlaceholderText('Имя пользователя...'), 'bob')
    await user.click(screen.getByRole('button', { name: 'Искать' }))

    await waitFor(() => {
      expect(screen.getByText('bob')).toBeInTheDocument()
    })

    await user.click(screen.getByRole('button', { name: 'Добавить' }))

    await waitFor(() => {
      expect(sendMock.mock.calls[0]?.[0]).toBe('user-3')
    })
  })
})
