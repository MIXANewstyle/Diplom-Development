import { apiClient } from './client'

export async function uploadImage(file: File): Promise<string> {
  const form = new FormData()
  form.append('file', file)
  const { data } = await apiClient.post<{ url: string }>('/api/v1/uploads/image', form)
  return data.url
}
