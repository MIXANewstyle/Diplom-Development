import { useQuery } from '@tanstack/react-query';
import { getMyProfile } from '../api';

export function useMyProfile() {
  return useQuery({
    queryKey: ['profile', 'me'],
    queryFn: getMyProfile,
  });
}
