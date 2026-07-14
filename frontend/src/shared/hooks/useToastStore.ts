import { create } from 'zustand';

export type ToastVariant = 'success' | 'info' | 'warning' | 'error';

export interface Toast {
  id: string;
  variant: ToastVariant;
  message: string;
  durationMs: number | null;
}

export type ToastInput = Omit<Toast, 'id' | 'durationMs'> & {
  durationMs?: number | null;
};

export interface ToastState {
  toasts: readonly Toast[];
  push: (input: ToastInput) => string;
  dismiss: (id: string) => void;
  clear: () => void;
}

const DEFAULT_DURATION_MS: Record<ToastVariant, number> = {
  success: 5000,
  info: 5000,
  warning: 8000,
  error: 8000,
};

const INITIAL_STATE = { toasts: [] as readonly Toast[] } satisfies Pick<ToastState, 'toasts'>;

function generateId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `toast-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

function resolveDuration(input: ToastInput): number | null {
  if (input.durationMs !== undefined) return input.durationMs;
  return DEFAULT_DURATION_MS[input.variant];
}

export const useToastStore = create<ToastState>((set) => ({
  ...INITIAL_STATE,

  push: (input) => {
    const toast: Toast = {
      id: generateId(),
      variant: input.variant,
      message: input.message,
      durationMs: resolveDuration(input),
    };
    set((state) => ({ toasts: [...state.toasts, toast] }));
    return toast.id;
  },

  dismiss: (id) => {
    set((state) => ({ toasts: state.toasts.filter((t) => t.id !== id) }));
  },

  clear: () => {
    set({ toasts: [] });
  },
}));

export function resetToastStoreForTest(): void {
  useToastStore.setState({ ...INITIAL_STATE });
}

type ToastHelperOptions = { durationMs?: number | null };

function helper(variant: ToastVariant) {
  return (message: string, options: ToastHelperOptions = {}): string =>
    useToastStore.getState().push({ variant, message, ...options });
}

export const toast = {
  success: helper('success'),
  info: helper('info'),
  warning: helper('warning'),
  error: helper('error'),
};

export function rateLimitMessage(retryAfterSeconds: number): string {
  if (retryAfterSeconds <= 0) return 'Please slow down and try again shortly.';
  const unit = retryAfterSeconds === 1 ? 'second' : 'seconds';
  return `Please wait ${retryAfterSeconds} ${unit} before trying again.`;
}
