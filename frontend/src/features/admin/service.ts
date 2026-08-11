import { apiClient } from '../../shared/api/client';
import type { Paginated } from '../../shared/types/envelopes';
import type { UserAccount, UserAccountSearchParams } from './types';

export async function searchUserAccounts(
  params: UserAccountSearchParams,
): Promise<Paginated<UserAccount>> {
  const body = params.emailContains ? { email_contains: params.emailContains } : {};
  const response = await apiClient.post<Paginated<UserAccount>>('/admin/users', body, {
    params: {
      page: params.page,
      per_page: params.perPage,
      is_suspended: params.isSuspended,
      is_deleted: params.isDeleted,
      is_verified: params.isVerified,
    },
  });
  return response.data;
}

export async function getUserAccount(id: string): Promise<UserAccount> {
  const response = await apiClient.get<UserAccount>(`/admin/users/${id}`);
  return response.data;
}
