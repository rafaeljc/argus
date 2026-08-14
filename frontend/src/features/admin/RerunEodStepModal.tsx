import { Button } from '../../shared/components/ui/Button';
import { Modal } from '../../shared/components/ui/Modal';
import { useForm } from '../../shared/hooks/useForm';
import { toast } from '../../shared/hooks/useToastStore';
import { rerunEodPipelineStep } from './service';
import { PIPELINE_STEPS, STEP_LABELS } from './types';
import type { EodPipelineStep } from './types';

export interface RerunEodStepModalProps {
  open: boolean;
  runId: string;
  step: EodPipelineStep;
  onClose: () => void;
  onRerun: () => void;
}

function Highlight({ children }: { children: string }) {
  return <span className="font-semibold text-slate-900">{children}</span>;
}

function RerunDescription({ step }: { step: EodPipelineStep }) {
  const downstreamSteps = PIPELINE_STEPS.slice(PIPELINE_STEPS.indexOf(step) + 1);
  const stepLabel = STEP_LABELS[step];

  if (downstreamSteps.length === 0) {
    return (
      <p className="text-sm text-slate-700">
        This re-runs <Highlight>{stepLabel}</Highlight>. Steps before it are left untouched.
      </p>
    );
  }

  return (
    <p className="text-sm text-slate-700">
      This re-runs <Highlight>{stepLabel}</Highlight> and every step after it (
      <Highlight>
        {downstreamSteps.map((downstream) => STEP_LABELS[downstream]).join(', ')}
      </Highlight>
      ). Steps before it are left untouched.
    </p>
  );
}

export function RerunEodStepModal({ open, runId, step, onClose, onRerun }: RerunEodStepModalProps) {
  const form = useForm<Record<string, never>>({
    initialValues: {},
    onSubmit: async () => {
      await rerunEodPipelineStep(runId, step);
      toast.success('Step re-run triggered.');
      form.reset();
      onClose();
      onRerun();
    },
  });

  const handleClose = () => {
    if (form.isSubmitting) return;
    form.reset();
    onClose();
  };

  return (
    <Modal open={open} onClose={handleClose} title={`Re-run ${STEP_LABELS[step]}`}>
      <form
        className="flex flex-col gap-4"
        onSubmit={(event) => {
          void form.handleSubmit(event);
        }}
        noValidate
      >
        <RerunDescription step={step} />

        {form.formError && (
          <p
            role="alert"
            className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700"
          >
            {form.formError}
          </p>
        )}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button type="button" variant="secondary" onClick={handleClose}>
            Cancel
          </Button>
          <Button type="submit" variant="primary" isLoading={form.isSubmitting}>
            Confirm re-run
          </Button>
        </div>
      </form>
    </Modal>
  );
}
