import { Button } from '../../shared/components/ui/Button';
import { DateField } from '../../shared/components/ui/DateField';
import { Modal } from '../../shared/components/ui/Modal';
import { NumberField } from '../../shared/components/ui/NumberField';
import { SegmentedField } from '../../shared/components/ui/SegmentedField';
import { TextField } from '../../shared/components/ui/TextField';
import { useForm } from '../../shared/hooks/useForm';
import { toast } from '../../shared/hooks/useToastStore';
import { createTransaction } from './service';
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

const TICKER_PATTERN = /^[A-Z.]{1,10}$/;
const QUANTITY_PATTERN = /^\d+(\.\d{1,6})?$/;

const SUCCESS_MESSAGE = 'Transaction added.';
const TICKER_ERROR = 'Ticker must be 1-10 uppercase letters or periods.';
const QUANTITY_ERROR = 'Quantity must be a positive number with up to 6 decimal places.';
const TRADE_DATE_REQUIRED_ERROR = 'Trade date is required.';
const TRADE_DATE_FUTURE_ERROR = 'Trade date cannot be in the future.';

type ValidationErrors = Partial<Record<keyof TransactionInput, string>>;

// Mirrors the backend's Clock.today() (America/New_York trading day), not UTC or the
// browser's local date — the future-date guard must agree with the server's own boundary,
// since that's what actually decides TRADE_DATE_FUTURE.
const MARKET_TIME_ZONE = 'America/New_York';
const todayFormatter = new Intl.DateTimeFormat('en-CA', { timeZone: MARKET_TIME_ZONE });

function todayIso(): string {
  return todayFormatter.format(new Date());
}

function validate(values: TransactionInput): ValidationErrors {
  const errors: ValidationErrors = {};

  if (!TICKER_PATTERN.test(values.ticker)) {
    errors.ticker = TICKER_ERROR;
  }

  if (!QUANTITY_PATTERN.test(values.quantity.trim()) || Number(values.quantity) <= 0) {
    errors.quantity = QUANTITY_ERROR;
  }

  if (!values.trade_date) {
    errors.trade_date = TRADE_DATE_REQUIRED_ERROR;
  } else if (values.trade_date > todayIso()) {
    errors.trade_date = TRADE_DATE_FUTURE_ERROR;
  }

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
