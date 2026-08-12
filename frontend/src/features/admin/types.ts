export interface UserAccount {
  id: string;
  email: string;
  is_verified: boolean;
  is_suspended: boolean;
  is_deleted: boolean;
  is_admin: boolean;
  created_at: string;
  deleted_at: string | null;
}

export interface UserAccountSearchParams {
  page: number;
  perPage: number;
  emailContains?: string | undefined;
  isSuspended?: boolean | undefined;
  isDeleted?: boolean | undefined;
  isVerified?: boolean | undefined;
}

export type UserAccountAction = 'suspend' | 'unsuspend' | 'delete';

export interface UserAccountActionResult {
  id: string;
  is_suspended: boolean;
  is_deleted: boolean;
  deleted_at: string | null;
}
