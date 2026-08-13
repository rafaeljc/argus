import { apiClient } from '../../shared/api/client';
import type { Paginated } from '../../shared/types/envelopes';
import type {
  AuditLogEntry,
  AuditLogSearchParams,
  UserAccount,
  UserAccountActionResult,
  UserAccountSearchParams,
} from './types';

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

async function performUserAccountAction(
  id: string,
  action: 'suspend' | 'unsuspend' | 'delete',
  reason: string,
): Promise<UserAccountActionResult> {
  const trimmed = reason.trim();
  const body = trimmed === '' ? {} : { reason: trimmed };
  const response = await apiClient.post<UserAccountActionResult>(
    `/admin/users/${id}/${action}`,
    body,
  );
  return response.data;
}

export function suspendUserAccount(id: string, reason: string): Promise<UserAccountActionResult> {
  return performUserAccountAction(id, 'suspend', reason);
}

export function unsuspendUserAccount(id: string, reason: string): Promise<UserAccountActionResult> {
  return performUserAccountAction(id, 'unsuspend', reason);
}

export function deleteUserAccount(id: string, reason: string): Promise<UserAccountActionResult> {
  return performUserAccountAction(id, 'delete', reason);
}

export async function searchAuditLog(
  params: AuditLogSearchParams,
): Promise<Paginated<AuditLogEntry>> {
  const response = await apiClient.get<Paginated<AuditLogEntry>>('/admin/audit-log', {
    params: {
      page: params.page,
      per_page: params.perPage,
      actor_id: params.actorId,
      target_user_id: params.targetUserId,
      action: params.action,
      from: params.from,
      to: params.to,
    },
  });
  return response.data;
}
