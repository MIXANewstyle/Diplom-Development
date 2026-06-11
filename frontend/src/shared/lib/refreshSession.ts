import { apiClient } from '../api/client';
import { useAuthStore } from '../stores/authStore';
import { queryClient } from '../api/queryClient';
import type { UserRole } from '../types/api';

const ROLE_RANKS: Record<UserRole, number> = {
  GUEST: 0,
  FREE: 1,
  BASIC: 2,
  AUTHOR: 3,
  ADMIN: 4,
};

/**
 * Calls the backend to issue a fresh JWT reflecting the user's current role,
 * updates the auth store, and invalidates relevant queries.
 */
export async function refreshSession(): Promise<UserRole> {
  const res = await apiClient.post<{ token: string }>('/api/v1/users/me/token');
  
  if (res.data && res.data.token) {
    useAuthStore.getState().setAuth(res.data.token);
    
    // Invalidate queries that might depend on the new role
    queryClient.invalidateQueries({ queryKey: ['billing'] });
    queryClient.invalidateQueries({ queryKey: ['chat'] });
    queryClient.invalidateQueries({ queryKey: ['feed'] });
  }

  const role = useAuthStore.getState().user?.role || 'GUEST';
  return role;
}

/**
 * Repeatedly refreshes the session until the user's role reaches at least `expectedAtLeast`,
 * or until `tries` are exhausted. This handles the async nature of role upgrades.
 */
export async function refreshSessionUntilUpgraded(
  expectedAtLeast: UserRole = 'BASIC',
  tries = 3
): Promise<UserRole> {
  const targetRank = ROLE_RANKS[expectedAtLeast];
  let currentRole: UserRole = useAuthStore.getState().user?.role || 'GUEST';

  for (let i = 0; i < tries; i++) {
    currentRole = await refreshSession();
    
    if (ROLE_RANKS[currentRole] >= targetRank) {
      break;
    }
    
    // Wait ~1.5s before retrying
    if (i < tries - 1) {
      await new Promise(r => setTimeout(r, 1500));
    }
  }

  return currentRole;
}
