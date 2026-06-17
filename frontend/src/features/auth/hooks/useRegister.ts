import { useMutation } from '@tanstack/react-query'
import { useLocation, useNavigate } from 'react-router-dom'
import { registerUser, loginUser } from '../api'
import { useAuthStore } from '../../../shared/stores/authStore'
import { getSafeRedirect } from '../../../shared/lib/redirect'
import type { RegisterRequest } from '../types'

export function useRegister() {
  const navigate = useNavigate()
  const location = useLocation()
  const setAuth = useAuthStore((state) => state.setAuth)

  return useMutation({
    mutationFn: async (data: RegisterRequest) => {
      await registerUser(data)
      const loginResponse = await loginUser({ email: data.email, password: data.password })
      return loginResponse
    },
    onSuccess: (data) => {
      setAuth(data.token)
      navigate(getSafeRedirect(location.search), { replace: true })
    },
  })
}
