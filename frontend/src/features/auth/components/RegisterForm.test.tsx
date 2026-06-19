import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api'
import { RegisterForm } from './RegisterForm'
import { renderWithProviders } from '../../../test/renderWithProviders'

describe('RegisterForm', () => {
  it('отправляет введённые данные при регистрации', async () => {
    const registerMock = vi.fn().mockResolvedValue({
      id: 'user-1',
      email: 'user@mail.ru',
      username: 'testuser',
    })
    const loginMock = vi.fn().mockResolvedValue({ token: 'jwt-token' })
    vi.spyOn(api, 'registerUser').mockImplementation(registerMock)
    vi.spyOn(api, 'loginUser').mockImplementation(loginMock)

    renderWithProviders(<RegisterForm />)
    const user = userEvent.setup()

    await user.type(screen.getByLabelText('Email'), 'user@mail.ru')
    await user.type(screen.getByLabelText(/Имя пользователя/), 'testuser')
    await user.type(screen.getByLabelText(/Полное имя/), 'Test User')
    await user.type(screen.getByLabelText('Пароль'), 'secret123')
    await user.type(screen.getByLabelText('Повторите пароль'), 'secret123')
    await user.click(screen.getByRole('button', { name: 'Зарегистрироваться' }))

    await waitFor(() => {
      expect(registerMock).toHaveBeenCalledWith({
        email: 'user@mail.ru',
        password: 'secret123',
        username: 'testuser',
        fullName: 'Test User',
      })
    })
  })
})
