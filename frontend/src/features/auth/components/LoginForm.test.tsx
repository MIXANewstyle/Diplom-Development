import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api'
import { LoginForm } from './LoginForm'
import { renderWithProviders } from '../../../test/renderWithProviders'

describe('LoginForm', () => {
  it('отправляет введённые данные при входе', async () => {
    const loginMock = vi.fn().mockResolvedValue({ token: 'jwt-token' })
    vi.spyOn(api, 'loginUser').mockImplementation(loginMock)

    renderWithProviders(<LoginForm />)
    const user = userEvent.setup()

    await user.type(screen.getByLabelText('Email'), 'user@mail.ru')
    await user.type(screen.getByLabelText('Пароль'), 'secret123')
    await user.click(screen.getByRole('button', { name: 'Войти' }))

    await waitFor(() => {
      expect(loginMock).toHaveBeenCalledWith({
        email: 'user@mail.ru',
        password: 'secret123',
      })
    })
  })
})
