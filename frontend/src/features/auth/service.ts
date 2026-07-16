import { apiClient } from '../../shared/api/client';
import type { LoginBody, SessionResult, SignupBody, SignupResult, VerifyEmailBody } from './types';

export async function login(body: LoginBody): Promise<SessionResult> {
  const response = await apiClient.post<SessionResult>('/auth/login', body, {
    skipGlobalAuthHandling: true,
    skipCsrfHeader: true,
  });
  return response.data;
}

export async function signup(body: SignupBody): Promise<SignupResult> {
  const response = await apiClient.post<SignupResult>('/auth/signup', body, {
    skipGlobalAuthHandling: true,
    skipCsrfHeader: true,
  });
  return response.data;
}

export async function verifyEmail(body: VerifyEmailBody): Promise<void> {
  await apiClient.post('/auth/verify-email', body, {
    skipGlobalAuthHandling: true,
    skipCsrfHeader: true,
  });
}
