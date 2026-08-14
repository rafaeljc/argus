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

export const ADMIN_ACTIONS = [
  'SUSPEND',
  'UNSUSPEND',
  'DELETE',
  'EOD_RUN',
  'EOD_STEP_RERUN',
] as const;

export type AdminAction = (typeof ADMIN_ACTIONS)[number];

export interface AuditLogEntry {
  id: string;
  actor_id: string;
  action: string;
  target_user_id: string | null;
  metadata: Record<string, unknown> | null;
  created_at: string;
}

export interface AuditLogSearchParams {
  page: number;
  perPage: number;
  actorId?: string | undefined;
  targetUserId?: string | undefined;
  action?: string | undefined;
  from?: string | undefined;
  to?: string | undefined;
}

export const PIPELINE_STEPS = ['symbols', 'prices', 'evaluate'] as const;

export type EodPipelineStep = (typeof PIPELINE_STEPS)[number];

export const STEP_LABELS: Record<EodPipelineStep, string> = {
  symbols: 'Symbols',
  prices: 'Prices',
  evaluate: 'Evaluate',
};

export type EodRunStatus = 'pending' | 'in_progress' | 'succeeded' | 'failed';

export type EodStepStatus = 'pending' | 'in_progress' | 'succeeded' | 'failed' | 'skipped';

export interface EodPipelineRun {
  run_id: string;
  run_date: string;
  trigger: 'cron' | 'admin';
  status: EodRunStatus;
  started_at: string;
  finished_at: string | null;
  step_symbols_status: EodStepStatus;
  step_prices_status: EodStepStatus;
  step_evaluate_status: EodStepStatus;
  error_message: string | null;
}

export const TRIGGER_LABELS: Record<EodPipelineRun['trigger'], string> = {
  cron: 'Cron',
  admin: 'Admin',
};

export interface EodPipelineRunSearchParams {
  page: number;
  perPage: number;
}

export interface EodStepRerunResult {
  run_id: string;
  step: EodPipelineStep;
  status: EodRunStatus;
  started_at: string;
}
