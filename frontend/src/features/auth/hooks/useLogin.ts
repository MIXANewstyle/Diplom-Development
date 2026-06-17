import { useMutation } from '@tanstack/react-query'
import { useLocation, useNavigate } from 'react-router-dom'
import { loginUser } from '../api'
import { useAuthStore } from '../../../shared/stores/authStore'
import { getSafeRedirect } from '../../../shared/lib/redirect'
import type { LoginRequest } from '../types'

export function useLogin() {
  const navigate = useNavigate()
  const location = useLocation()
  const setAuth = useAuthStore((state) => state.setAuth)

  return useMutation({
    mutationFn: (data: LoginRequest) => loginUser(data),
    onSuccess: (data) => {
      setAuth(data.token)
      navigate(getSafeRedirect(location.search), { replace: true })
    },
  })
}
