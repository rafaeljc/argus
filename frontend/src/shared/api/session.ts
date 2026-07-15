import type { CurrentUser } from '../types/user';
import { apiClient } from './client';

export async function fetchCurrentUser(): Promise<CurrentUser> {
  // The /account/me probe is a state read, not a route guard: a 401 means
  // "anonymous", nothing more. Opt out of the global 401 handler so the probe
  // never triggers a route redirect. Route protection is RequireAuth's job.
  const response = await apiClient.get<CurrentUser>('/account/me', {
    skipGlobalAuthHandling: true,
  });
  return response.data;
}
