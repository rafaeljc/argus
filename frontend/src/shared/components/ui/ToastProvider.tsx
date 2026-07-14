import { useEffect } from 'react';
import { clsx } from 'clsx';
import {
  CheckCircleIcon,
  ExclamationTriangleIcon,
  InformationCircleIcon,
  XCircleIcon,
  XMarkIcon,
} from '@heroicons/react/24/outline';

import { useToastStore, type Toast, type ToastVariant } from '../../hooks/useToastStore';

const VARIANT_STYLES: Record<ToastVariant, string> = {
  success: 'border-emerald-200 bg-emerald-50 text-emerald-900',
  info: 'border-slate-200 bg-white text-slate-900',
  warning: 'border-amber-200 bg-amber-50 text-amber-900',
  error: 'border-red-200 bg-red-50 text-red-900',
};

const VARIANT_ICON_STYLES: Record<ToastVariant, string> = {
  success: 'text-emerald-600',
  info: 'text-brand',
  warning: 'text-amber-600',
  error: 'text-red-600',
};

function VariantIcon({ variant }: { variant: ToastVariant }) {
  const className = clsx('h-5 w-5 flex-shrink-0', VARIANT_ICON_STYLES[variant]);
  switch (variant) {
    case 'success':
      return <CheckCircleIcon aria-hidden="true" className={className} />;
    case 'warning':
      return <ExclamationTriangleIcon aria-hidden="true" className={className} />;
    case 'error':
      return <XCircleIcon aria-hidden="true" className={className} />;
    case 'info':
    default:
      return <InformationCircleIcon aria-hidden="true" className={className} />;
  }
}

interface ToastItemProps {
  toast: Toast;
  onDismiss: (id: string) => void;
}

function ToastItem({ toast, onDismiss }: ToastItemProps) {
  const { id, variant, message, durationMs } = toast;

  useEffect(() => {
    if (durationMs === null) return;
    const handle = window.setTimeout(() => onDismiss(id), durationMs);
    return () => window.clearTimeout(handle);
  }, [id, durationMs, onDismiss]);

  const role = variant === 'error' ? 'alert' : 'status';

  return (
    <li className="contents">
      <div
        role={role}
        className={clsx(
          'pointer-events-auto flex items-start gap-3 rounded-lg border px-4 py-3 shadow-sm',
          VARIANT_STYLES[variant],
        )}
      >
        <VariantIcon variant={variant} />
        <p className="flex-1 text-sm leading-5">{message}</p>
        <button
          type="button"
          onClick={() => onDismiss(id)}
          aria-label="Dismiss notification"
          className="inline-flex flex-shrink-0 items-center justify-center rounded-md p-1 text-slate-500 hover:bg-slate-100 hover:text-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand"
        >
          <XMarkIcon aria-hidden="true" className="h-4 w-4" />
        </button>
      </div>
    </li>
  );
}

export function ToastProvider() {
  const toasts = useToastStore((state) => state.toasts);
  const dismiss = useToastStore((state) => state.dismiss);

  return (
    <section
      aria-label="Notifications"
      aria-live="polite"
      className="pointer-events-none fixed inset-x-4 top-4 z-50 sm:left-auto sm:right-4 sm:w-96"
    >
      <ol className="flex flex-col gap-2">
        {toasts.map((toast) => (
          <ToastItem key={toast.id} toast={toast} onDismiss={dismiss} />
        ))}
      </ol>
    </section>
  );
}
