import type { CurrentUser } from '../types/user';
import { apiClient } from './client';

export async function fetchCurrentUser(): Promise<CurrentUser> {
  const response = await apiClient.get<CurrentUser>('/account/me');
  return response.data;
}
