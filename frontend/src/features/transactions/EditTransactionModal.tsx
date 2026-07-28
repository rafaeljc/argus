import { Button } from '../../shared/components/ui/Button';
import { DateField } from '../../shared/components/ui/DateField';
import { Modal } from '../../shared/components/ui/Modal';
import { NumberField } from '../../shared/components/ui/NumberField';
import { SegmentedField } from '../../shared/components/ui/SegmentedField';
import { TextField } from '../../shared/components/ui/TextField';
import { useForm } from '../../shared/hooks/useForm';
import { toast } from '../../shared/hooks/useToastStore';
import { updateTransaction } from './service';
import { todayIso, validateQuantity, validateTradeDate } from './transactionForm';
import type { Transaction, TransactionPatch } from './types';

const OPERATION_OPTIONS = [
  { value: 'BUY', label: 'Buy' },
  { value: 'SELL', label: 'Sell' },
] as const;

const SUCCESS_MESSAGE = 'Transaction updated.';

interface EditableValues {
  operation: Transaction['operation'];
  quantity: string;
  trade_date: string;
}

type ValidationErrors = Partial<Record<keyof EditableValues, string>>;

function toValues(transaction: Transaction): EditableValues {
  return {
    operation: transaction.operation,
    quantity: transaction.quantity,
    trade_date: transaction.trade_date,
  };
}

function validate(values: EditableValues): ValidationErrors {
  const errors: ValidationErrors = {};

  const quantityError = validateQuantity(values.quantity);
  if (quantityError) errors.quantity = quantityError;

  const tradeDateError = validateTradeDate(values.trade_date);
  if (tradeDateError) errors.trade_date = tradeDateError;

  return errors;
}

function buildPatch(initial: EditableValues, current: EditableValues): TransactionPatch {
  const patch: TransactionPatch = {};
  if (current.operation !== initial.operation) patch.operation = current.operation;
  if (current.quantity !== initial.quantity) patch.quantity = current.quantity;
  if (current.trade_date !== initial.trade_date) patch.trade_date = current.trade_date;
  return patch;
}

function isDirty(initial: EditableValues, current: EditableValues): boolean {
  return (
    current.operation !== initial.operation ||
    current.quantity !== initial.quantity ||
    current.trade_date !== initial.trade_date
  );
}

export interface EditTransactionModalProps {
  open: boolean;
  transaction: Transaction;
  onClose: () => void;
  onUpdated: (transaction: Transaction) => void;
}

export function EditTransactionModal({
  open,
  transaction,
  onClose,
  onUpdated,
}: EditTransactionModalProps) {
  const initialValues = toValues(transaction);

  const form = useForm<EditableValues>({
    initialValues,
    onSubmit: async (values) => {
      const errors = validate(values);
      if (Object.keys(errors).length > 0) {
        form.setFieldErrors(errors);
        return;
      }

      const patch = buildPatch(initialValues, values);
      const updated = await updateTransaction(transaction.id, patch);
      toast.success(SUCCESS_MESSAGE);
      onUpdated(updated);
      onClose();
    },
  });

  const handleClose = () => {
    if (form.isSubmitting) return;
    form.reset();
    onClose();
  };

  return (
    <Modal open={open} onClose={handleClose} title="Edit transaction">
      <form
        className="flex flex-col gap-4"
        onSubmit={(event) => {
          void form.handleSubmit(event);
        }}
        noValidate
      >
        {form.formError && (
          <p
            role="alert"
            className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700"
          >
            {form.formError}
          </p>
        )}

        <TextField label="Ticker" value={transaction.ticker} disabled />

        <SegmentedField
          label="Operation"
          name="operation"
          options={OPERATION_OPTIONS}
          value={form.values.operation}
          onChange={form.handleChange('operation')}
          error={form.fieldErrors.operation ?? ''}
        />

        <NumberField
          label="Quantity"
          required
          value={form.values.quantity}
          onChange={form.handleChange('quantity')}
          error={form.fieldErrors.quantity ?? ''}
        />

        <DateField
          label="Trade date"
          required
          max={todayIso()}
          value={form.values.trade_date}
          onChange={form.handleChange('trade_date')}
          error={form.fieldErrors.trade_date ?? ''}
        />

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button type="button" variant="secondary" onClick={handleClose}>
            Cancel
          </Button>
          <Button
            type="submit"
            isLoading={form.isSubmitting}
            disabled={!isDirty(initialValues, form.values)}
          >
            Save changes
          </Button>
        </div>
      </form>
    </Modal>
  );
}
