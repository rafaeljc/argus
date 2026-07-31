import { Button } from '../../shared/components/ui/Button';
import { Modal } from '../../shared/components/ui/Modal';
import { NumberField } from '../../shared/components/ui/NumberField';
import { SegmentedField } from '../../shared/components/ui/SegmentedField';
import { SelectField } from '../../shared/components/ui/SelectField';
import { useForm } from '../../shared/hooks/useForm';
import { toast } from '../../shared/hooks/useToastStore';
import {
  DIRECTION_OPTIONS,
  WINDOW_DAYS_OPTIONS,
  toAlertRuleInput,
  validateThreshold,
  validateWindowDays,
  type AlertRuleFormValues,
} from './alertForm';
import { createAlertRule } from './service';

const INITIAL_VALUES: AlertRuleFormValues = {
  direction: 'UP',
  threshold: '',
  window_days: '7',
};

const SUCCESS_MESSAGE = 'Alert rule created.';

type ValidationErrors = Partial<Record<keyof AlertRuleFormValues, string>>;

function validate(values: AlertRuleFormValues): ValidationErrors {
  const errors: ValidationErrors = {};

  const thresholdError = validateThreshold(values.threshold);
  if (thresholdError) errors.threshold = thresholdError;

  const windowDaysError = validateWindowDays(values.window_days);
  if (windowDaysError) errors.window_days = windowDaysError;

  return errors;
}

export interface CreateAlertRuleModalProps {
  open: boolean;
  onClose: () => void;
  onCreated: () => void;
}

export function CreateAlertRuleModal({ open, onClose, onCreated }: CreateAlertRuleModalProps) {
  const form = useForm<AlertRuleFormValues>({
    initialValues: INITIAL_VALUES,
    onSubmit: async (values) => {
      const errors = validate(values);
      if (Object.keys(errors).length > 0) {
        form.setFieldErrors(errors);
        return;
      }

      await createAlertRule(toAlertRuleInput(values));
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
    <Modal open={open} onClose={handleClose} title="Create alert rule">
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

        <SegmentedField
          label="Direction"
          name="direction"
          options={DIRECTION_OPTIONS}
          value={form.values.direction}
          onChange={form.handleChange('direction')}
          error={form.fieldErrors.direction ?? ''}
        />

        <NumberField
          label="Threshold"
          required
          hint="Between 0.5 and 100, in steps of 0.1"
          value={form.values.threshold}
          onChange={form.handleChange('threshold')}
          error={form.fieldErrors.threshold ?? ''}
        />

        <SelectField
          label="Window"
          options={WINDOW_DAYS_OPTIONS}
          value={form.values.window_days}
          onChange={form.handleChange('window_days')}
          error={form.fieldErrors.window_days ?? ''}
        />

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button type="button" variant="secondary" onClick={handleClose}>
            Cancel
          </Button>
          <Button type="submit" isLoading={form.isSubmitting}>
            Save rule
          </Button>
        </div>
      </form>
    </Modal>
  );
}
