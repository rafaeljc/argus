import { apiClient } from '../../shared/api/client';
import type { DeleteAccountBody } from './types';

export async function deleteAccount(currentPassword: string): Promise<void> {
  const body: DeleteAccountBody = { current_password: currentPassword };
  await apiClient.delete('/account/me', { data: body });
}
