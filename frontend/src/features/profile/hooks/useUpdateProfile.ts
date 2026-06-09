import { useMutation, useQueryClient } from '@tanstack/react-query';
import { updateProfile } from '../api';
import type { ProfileUpdateBody } from '../types';

export function useUpdateProfile() {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: (body: ProfileUpdateBody) => updateProfile(body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profile', 'me'] });
    },
  });
}
