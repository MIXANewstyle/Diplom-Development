import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'
import { useAuthStore } from '../shared/stores/authStore'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  useAuthStore.setState({ token: null, user: null })
})
