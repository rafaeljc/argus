import type { ChangeEvent } from 'react';
import { useNavigate } from 'react-router-dom';

import { ApiError } from '../../shared/api/errors';
import { Button } from '../../shared/components/ui/Button';
import { DateField } from '../../shared/components/ui/DateField';
import { Modal } from '../../shared/components/ui/Modal';
import { useForm } from '../../shared/hooks/useForm';
import { toast } from '../../shared/hooks/useToastStore';
import { triggerEodPipelineRun } from './service';

const RUN_DATE_FUTURE_ERROR = 'Run date cannot be in the future.';
const RUN_DATE_CONFLICT_ERROR = 'A run is already in progress for that date.';

// Mirrors the backend's Clock.today() (America/New_York trading day), not UTC or the
// browser's local date — the future-date guard must agree with the server's own boundary,
// since @PastOrPresent on run_date is what actually decides the rejection.
const MARKET_TIME_ZONE = 'America/New_York';
const todayFormatter = new Intl.DateTimeFormat('en-CA', { timeZone: MARKET_TIME_ZONE });

function todayIso(): string {
  return todayFormatter.format(new Date());
}

interface TriggerFormValues {
  runDate: string;
}

const INITIAL_VALUES: TriggerFormValues = { runDate: '' };

export interface TriggerEodRunModalProps {
  open: boolean;
  onClose: () => void;
}

export function TriggerEodRunModal({ open, onClose }: TriggerEodRunModalProps) {
  const navigate = useNavigate();

  const form = useForm<TriggerFormValues>({
    initialValues: INITIAL_VALUES,
    onSubmit: async ({ runDate }) => {
      if (runDate !== '' && runDate > todayIso()) {
        form.setFieldErrors({ runDate: RUN_DATE_FUTURE_ERROR });
        return;
      }

      try {
        const run = await triggerEodPipelineRun(runDate);
        toast.success('Run triggered.');
        form.reset();
        onClose();
        navigate(`/admin/eod-pipeline/${run.run_id}`);
      } catch (error) {
        if (error instanceof ApiError && error.status === 409) {
          form.setFieldErrors({ runDate: RUN_DATE_CONFLICT_ERROR });
          return;
        }
        throw error;
      }
    },
  });

  const handleClose = () => {
    if (form.isSubmitting) return;
    form.reset();
    onClose();
  };

  const handleRunDateChange = (event: ChangeEvent<HTMLInputElement>) => {
    form.setValue('runDate', event.currentTarget.value);
    if (form.fieldErrors.runDate !== undefined) form.setFieldErrors({});
  };

  return (
    <Modal open={open} onClose={handleClose} title="Trigger EOD run">
      <form
        className="flex flex-col gap-4"
        onSubmit={(event) => {
          void form.handleSubmit(event);
        }}
        noValidate
      >
        <p className="text-sm text-slate-700">
          Runs the end-of-day pipeline for the given date. Leave blank to run for today.
        </p>

        {form.formError && (
          <p
            role="alert"
            className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700"
          >
            {form.formError}
          </p>
        )}

        <DateField
          label="Run date (optional)"
          hint="Defaults to today when left blank."
          value={form.values.runDate}
          onChange={handleRunDateChange}
          error={form.fieldErrors.runDate ?? ''}
        />

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button type="button" variant="secondary" onClick={handleClose}>
            Cancel
          </Button>
          <Button type="submit" variant="primary" isLoading={form.isSubmitting}>
            Confirm trigger
          </Button>
        </div>
      </form>
    </Modal>
  );
}
