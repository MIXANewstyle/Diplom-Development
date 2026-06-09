import { apiClient } from '../../shared/api/client';
import type { MyProfile, ProfileUpdateBody } from './types';

export async function getMyProfile(): Promise<MyProfile> {
  const { data } = await apiClient.get<MyProfile>('/api/v1/users/me/profile');
  return data;
}

export async function updateProfile(body: ProfileUpdateBody): Promise<void> {
  await apiClient.put('/api/v1/users/me/profile', body);
}
