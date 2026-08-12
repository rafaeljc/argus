import type { ChangeEvent } from 'react';

import { Button } from '../../shared/components/ui/Button';
import { Modal } from '../../shared/components/ui/Modal';
import { TextAreaField } from '../../shared/components/ui/TextAreaField';
import { useForm } from '../../shared/hooks/useForm';
import { toast } from '../../shared/hooks/useToastStore';
import { deleteUserAccount, suspendUserAccount, unsuspendUserAccount } from './service';
import type { UserAccount, UserAccountAction, UserAccountActionResult } from './types';

const REASON_MAX_LENGTH = 1000;

interface ActionFormValues {
  reason: string;
}

const INITIAL_VALUES: ActionFormValues = { reason: '' };

interface ActionDescriptor {
  title: string;
  confirmLabel: string;
  body: (email: string) => string;
  successMessage: string;
  submit: (id: string, reason: string) => Promise<UserAccountActionResult>;
}

const ACTIONS: Record<UserAccountAction, ActionDescriptor> = {
  suspend: {
    title: 'Suspend user',
    confirmLabel: 'Confirm suspend',
    body: (email) =>
      `This signs ${email} out of every session and blocks further access until unsuspended.`,
    successMessage: 'User suspended.',
    submit: suspendUserAccount,
  },
  unsuspend: {
    title: 'Unsuspend user',
    confirmLabel: 'Confirm unsuspend',
    body: (email) =>
      `This restores ${email}'s access. Sessions ended by the earlier suspension stay signed out.`,
    successMessage: 'User unsuspended.',
    submit: unsuspendUserAccount,
  },
  delete: {
    title: 'Delete user',
    confirmLabel: 'Confirm delete',
    body: (email) => `This soft-deletes ${email} and signs them out of every session.`,
    successMessage: 'User deleted.',
    submit: deleteUserAccount,
  },
};

export interface UserAccountActionModalProps {
  open: boolean;
  action: UserAccountAction;
  account: UserAccount;
  onClose: () => void;
  onApplied: (result: UserAccountActionResult) => void;
}

export function UserAccountActionModal({
  open,
  action,
  account,
  onClose,
  onApplied,
}: UserAccountActionModalProps) {
  const descriptor = ACTIONS[action];

  const form = useForm<ActionFormValues>({
    initialValues: INITIAL_VALUES,
    onSubmit: async ({ reason }) => {
      const trimmed = reason.trim();
      if (trimmed.length > REASON_MAX_LENGTH) {
        form.setFieldErrors({ reason: `Reason must be ${REASON_MAX_LENGTH} characters or fewer.` });
        return;
      }

      const result = await descriptor.submit(account.id, reason);
      toast.success(descriptor.successMessage);
      form.reset();
      onApplied(result);
      onClose();
    },
  });

  const handleClose = () => {
    if (form.isSubmitting) return;
    form.reset();
    onClose();
  };

  const handleReasonChange = (event: ChangeEvent<HTMLTextAreaElement>) => {
    form.setValue('reason', event.currentTarget.value);
    if (form.fieldErrors.reason !== undefined) form.setFieldErrors({});
  };

  return (
    <Modal open={open} onClose={handleClose} title={descriptor.title}>
      <form
        className="flex flex-col gap-4"
        onSubmit={(event) => {
          void form.handleSubmit(event);
        }}
        noValidate
      >
        <p className="text-sm text-slate-700">{descriptor.body(account.email)}</p>

        {form.formError && (
          <p
            role="alert"
            className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700"
          >
            {form.formError}
          </p>
        )}

        <TextAreaField
          label="Reason (optional)"
          hint="Recorded on the audit log."
          value={form.values.reason}
          onChange={handleReasonChange}
          error={form.fieldErrors.reason ?? ''}
        />

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button type="button" variant="secondary" onClick={handleClose}>
            Cancel
          </Button>
          <Button type="submit" variant="danger" isLoading={form.isSubmitting}>
            {descriptor.confirmLabel}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
