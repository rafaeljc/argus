import { useState } from 'react';

import { Button } from '../../shared/components/ui/Button';
import { Modal } from '../../shared/components/ui/Modal';
import { ApiError } from '../../shared/api/errors';
import { toast } from '../../shared/hooks/useToastStore';
import { deleteTransaction } from './service';
import type { Transaction } from './types';

const SUCCESS_MESSAGE = 'Transaction deleted.';
const GENERIC_ERROR = 'Something went wrong. Please try again.';

export interface DeleteTransactionModalProps {
  open: boolean;
  transaction: Transaction;
  onClose: () => void;
  onDeleted: () => void;
}

export function DeleteTransactionModal({
  open,
  transaction,
  onClose,
  onDeleted,
}: DeleteTransactionModalProps) {
  const [isDeleting, setIsDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleClose = () => {
    if (isDeleting) return;
    setError(null);
    onClose();
  };

  const handleConfirm = async () => {
    setIsDeleting(true);
    setError(null);

    try {
      await deleteTransaction(transaction.id);
      toast.success(SUCCESS_MESSAGE);
      onDeleted();
    } catch (err: unknown) {
      setError(err instanceof ApiError ? err.message : GENERIC_ERROR);
    } finally {
      setIsDeleting(false);
    }
  };

  return (
    <Modal open={open} onClose={handleClose} title="Delete transaction">
      <div className="flex flex-col gap-4">
        <p className="text-sm text-slate-700">
          This will permanently delete the {transaction.operation.toLowerCase()} of{' '}
          {transaction.quantity} {transaction.ticker} on {transaction.trade_date}.
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
            Cancel
          </Button>
          <Button
            type="button"
            variant="danger"
            isLoading={isDeleting}
            onClick={() => {
              void handleConfirm();
            }}
          >
            Confirm delete
          </Button>
        </div>
      </div>
    </Modal>
  );
}
