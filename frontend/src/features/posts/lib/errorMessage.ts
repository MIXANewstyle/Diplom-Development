import type { AxiosError } from 'axios'

const PATCH_403 = 'Окно редактирования истекло (15 минут)'
const PATCH_409 = 'Комментарий уже удалён'
const REPLY_422 = 'Нельзя ответить на ответ — отвечайте только на корневой комментарий'

type Operation = 'create' | 'update' | 'delete'

export function getCommentErrorMessage(err: unknown, op: Operation): string {
  const axiosErr = err as AxiosError<{ message?: string }> | null
  const status = axiosErr?.response?.status

  if (op === 'update' && status === 403) return PATCH_403
  if (op === 'update' && status === 409) return PATCH_409
  if (op === 'create' && status === 422) return REPLY_422

  return (
    axiosErr?.response?.data?.message ??
    axiosErr?.message ??
    'Произошла ошибка'
  )
}
