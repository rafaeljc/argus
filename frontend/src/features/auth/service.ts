import { apiClient } from '../../shared/api/client';
import type { LoginBody, SessionResult } from './types';

export async function login(body: LoginBody): Promise<SessionResult> {
  const response = await apiClient.post<SessionResult>('/auth/login', body, {
    skipGlobalAuthHandling: true,
    skipCsrfHeader: true,
  });
  return response.data;
}
