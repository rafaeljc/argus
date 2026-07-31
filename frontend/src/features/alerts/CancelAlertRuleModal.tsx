import { useState } from 'react';

import { ApiError } from '../../shared/api/errors';
import { Button } from '../../shared/components/ui/Button';
import { Modal } from '../../shared/components/ui/Modal';
import { toast } from '../../shared/hooks/useToastStore';
import { summarizeRule } from './alertForm';
import { deleteAlertRule } from './service';
import type { AlertRule } from './types';

const SUCCESS_MESSAGE = 'Alert rule cancelled.';
const GENERIC_ERROR = 'Something went wrong. Please try again.';

export interface CancelAlertRuleModalProps {
  open: boolean;
  rule: AlertRule;
  onClose: () => void;
  onCancelled: () => void;
}

export function CancelAlertRuleModal({
  open,
  rule,
  onClose,
  onCancelled,
}: CancelAlertRuleModalProps) {
  const [isCancelling, setIsCancelling] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleClose = () => {
    if (isCancelling) return;
    setError(null);
    onClose();
  };

  const handleConfirm = async () => {
    setIsCancelling(true);
    setError(null);

    try {
      await deleteAlertRule(rule.id);
      toast.success(SUCCESS_MESSAGE);
      onCancelled();
    } catch (err: unknown) {
      setError(err instanceof ApiError ? err.message : GENERIC_ERROR);
    } finally {
      setIsCancelling(false);
    }
  };

  return (
    <Modal open={open} onClose={handleClose} title="Cancel alert rule">
      <div className="flex flex-col gap-4">
        <p className="text-sm text-slate-700">
          {summarizeRule(rule)}. This rule will be permanently cancelled.
        </p>

        {error && (
          <p
            role="alert"
            className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700"
          >
            {error}
          </p>
        )}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button type="button" variant="secondary" onClick={handleClose}>
            Keep rule
          </Button>
          <Button
            type="button"
            variant="danger"
            isLoading={isCancelling}
            onClick={() => {
              void handleConfirm();
            }}
          >
            Confirm cancel rule
          </Button>
        </div>
      </div>
    </Modal>
  );
}
