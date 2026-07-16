import { useNavigate } from 'react-router-dom';

import { Button } from '../../shared/components/ui/Button';
import { Modal } from '../../shared/components/ui/Modal';
import { PasswordField } from '../../shared/components/ui/PasswordField';
import { useAuthStore } from '../../shared/hooks/useAuthStore';
import { useForm } from '../../shared/hooks/useForm';
import { toast } from '../../shared/hooks/useToastStore';
import { deleteAccount } from './service';
import type { DeleteAccountBody } from './types';

const INITIAL_VALUES: DeleteAccountBody = { current_password: '' };
const SUCCESS_MESSAGE = 'Your account has been deleted.';

export interface DeleteAccountModalProps {
  open: boolean;
  onClose: () => void;
}

export function DeleteAccountModal({ open, onClose }: DeleteAccountModalProps) {
  const navigate = useNavigate();
  const clearAuth = useAuthStore((state) => state.clearAuth);

  const form = useForm<DeleteAccountBody>({
    initialValues: INITIAL_VALUES,
    onSubmit: async ({ current_password }) => {
      await deleteAccount(current_password);
      clearAuth();
      toast.success(SUCCESS_MESSAGE);
      navigate('/login', { replace: true });
    },
  });

  const handleClose = () => {
    if (form.isSubmitting) return;
    form.reset();
    onClose();
  };

  return (
    <Modal open={open} onClose={handleClose} title="Delete account">
      <form
        className="flex flex-col gap-4"
        onSubmit={(event) => {
          void form.handleSubmit(event);
        }}
        noValidate
      >
        <p className="text-sm text-slate-700">
          This will permanently deactivate your account and sign you out. Enter your current
          password to confirm.
        </p>

        {form.formError && (
          <p
            role="alert"
            className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700"
          >
            {form.formError}
          </p>
        )}

        <PasswordField
          label="Current password"
          autoComplete="current-password"
          required
          value={form.values.current_password}
          onChange={form.handleChange('current_password')}
          error={form.fieldErrors.current_password ?? ''}
        />

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button type="button" variant="secondary" onClick={handleClose}>
            Cancel
          </Button>
          <Button type="submit" variant="danger" isLoading={form.isSubmitting}>
            Confirm delete
          </Button>
        </div>
      </form>
    </Modal>
  );
}
