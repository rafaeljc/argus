import { Button } from '../../shared/components/ui/Button';
import { DateField } from '../../shared/components/ui/DateField';
import { Modal } from '../../shared/components/ui/Modal';
import { NumberField } from '../../shared/components/ui/NumberField';
import { SegmentedField } from '../../shared/components/ui/SegmentedField';
import { TextField } from '../../shared/components/ui/TextField';
import { useForm } from '../../shared/hooks/useForm';
import { toast } from '../../shared/hooks/useToastStore';
import { createTransaction } from './service';
import { todayIso, validateQuantity, validateTicker, validateTradeDate } from './transactionForm';
import type { TransactionInput } from './types';

const INITIAL_VALUES: TransactionInput = {
  ticker: '',
  operation: 'BUY',
  quantity: '',
  trade_date: '',
};

const OPERATION_OPTIONS = [
  { value: 'BUY', label: 'Buy' },
  { value: 'SELL', label: 'Sell' },
] as const;

const SUCCESS_MESSAGE = 'Transaction added.';

type ValidationErrors = Partial<Record<keyof TransactionInput, string>>;

function validate(values: TransactionInput): ValidationErrors {
  const errors: ValidationErrors = {};

  const tickerError = validateTicker(values.ticker);
  if (tickerError) errors.ticker = tickerError;

  const quantityError = validateQuantity(values.quantity);
  if (quantityError) errors.quantity = quantityError;

  const tradeDateError = validateTradeDate(values.trade_date);
  if (tradeDateError) errors.trade_date = tradeDateError;

  return errors;
}

export interface CreateTransactionModalProps {
  open: boolean;
  onClose: () => void;
  onCreated: () => void;
}

export function CreateTransactionModal({ open, onClose, onCreated }: CreateTransactionModalProps) {
  const form = useForm<TransactionInput>({
    initialValues: INITIAL_VALUES,
    onSubmit: async (values) => {
      const normalized: TransactionInput = { ...values, ticker: values.ticker.toUpperCase() };
      const errors = validate(normalized);
      if (Object.keys(errors).length > 0) {
        form.setFieldErrors(errors);
        return;
      }

      await createTransaction(normalized);
      toast.success(SUCCESS_MESSAGE);
      form.reset();
      onCreated();
      onClose();
    },
  });

  const handleClose = () => {
    if (form.isSubmitting) return;
    form.reset();
    onClose();
  };

  return (
    <Modal open={open} onClose={handleClose} title="Add transaction">
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

        <TextField
          label="Ticker"
          required
          value={form.values.ticker}
          onChange={form.handleChange('ticker')}
          onBlur={(event) => form.setValue('ticker', event.target.value.toUpperCase())}
          error={form.fieldErrors.ticker ?? ''}
        />

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
          <Button type="submit" isLoading={form.isSubmitting}>
            Save transaction
          </Button>
        </div>
      </form>
    </Modal>
  );
}
