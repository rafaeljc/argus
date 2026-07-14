import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { rateLimitMessage, resetToastStoreForTest, toast, useToastStore } from './useToastStore';

describe('useToastStore', () => {
  beforeEach(() => {
    resetToastStoreForTest();
  });

  afterEach(() => {
    resetToastStoreForTest();
  });

  it('starts with an empty toast queue', () => {
    expect(useToastStore.getState().toasts).toEqual([]);
  });

  it('push appends a toast and returns its generated id', () => {
    const id = useToastStore.getState().push({ variant: 'info', message: 'hello' });

    const { toasts } = useToastStore.getState();
    expect(toasts).toHaveLength(1);
    expect(toasts[0]?.id).toBe(id);
    expect(toasts[0]?.variant).toBe('info');
    expect(toasts[0]?.message).toBe('hello');
  });

  it('assigns unique ids across successive pushes', () => {
    const idA = useToastStore.getState().push({ variant: 'info', message: 'a' });
    const idB = useToastStore.getState().push({ variant: 'info', message: 'b' });

    expect(idA).not.toBe(idB);
    expect(useToastStore.getState().toasts.map((t) => t.id)).toEqual([idA, idB]);
  });

  it('applies default durations by variant', () => {
    useToastStore.getState().push({ variant: 'success', message: 's' });
    useToastStore.getState().push({ variant: 'info', message: 'i' });
    useToastStore.getState().push({ variant: 'warning', message: 'w' });
    useToastStore.getState().push({ variant: 'error', message: 'e' });

    const [s, i, w, e] = useToastStore.getState().toasts;
    expect(s?.durationMs).toBe(5000);
    expect(i?.durationMs).toBe(5000);
    expect(w?.durationMs).toBe(8000);
    expect(e?.durationMs).toBe(8000);
  });

  it('respects an explicit durationMs override, including null for sticky toasts', () => {
    const idSticky = useToastStore.getState().push({
      variant: 'error',
      message: 'stay',
      durationMs: null,
    });
    const idBrief = useToastStore.getState().push({
      variant: 'info',
      message: 'go',
      durationMs: 100,
    });

    const byId = new Map(useToastStore.getState().toasts.map((t) => [t.id, t]));
    expect(byId.get(idSticky)?.durationMs).toBeNull();
    expect(byId.get(idBrief)?.durationMs).toBe(100);
  });

  it('dismiss removes only the matching toast', () => {
    const idA = useToastStore.getState().push({ variant: 'info', message: 'a' });
    const idB = useToastStore.getState().push({ variant: 'info', message: 'b' });

    useToastStore.getState().dismiss(idA);

    expect(useToastStore.getState().toasts.map((t) => t.id)).toEqual([idB]);
  });

  it('dismiss is a no-op when the id is unknown', () => {
    const id = useToastStore.getState().push({ variant: 'info', message: 'a' });

    useToastStore.getState().dismiss('does-not-exist');

    expect(useToastStore.getState().toasts.map((t) => t.id)).toEqual([id]);
  });

  it('clear removes every toast', () => {
    useToastStore.getState().push({ variant: 'info', message: 'a' });
    useToastStore.getState().push({ variant: 'info', message: 'b' });

    useToastStore.getState().clear();

    expect(useToastStore.getState().toasts).toEqual([]);
  });

  it('does not mutate the previous toasts array on push', () => {
    const before = useToastStore.getState().toasts;
    useToastStore.getState().push({ variant: 'info', message: 'a' });
    const after = useToastStore.getState().toasts;

    expect(after).not.toBe(before);
    expect(before).toEqual([]);
  });
});

describe('toast helpers', () => {
  beforeEach(() => {
    resetToastStoreForTest();
  });

  it.each(['success', 'info', 'warning', 'error'] as const)(
    'toast.%s pushes a toast of that variant',
    (variant) => {
      toast[variant]('hey');

      const [entry] = useToastStore.getState().toasts;
      expect(entry?.variant).toBe(variant);
      expect(entry?.message).toBe('hey');
    },
  );

  it('toast.error forwards an explicit sticky durationMs override', () => {
    toast.error('stay', { durationMs: null });

    expect(useToastStore.getState().toasts[0]?.durationMs).toBeNull();
  });
});

describe('rateLimitMessage', () => {
  it('formats a positive Retry-After in seconds', () => {
    expect(rateLimitMessage(30)).toBe('Please wait 30 seconds before trying again.');
  });

  it('uses the singular form when Retry-After is 1 second', () => {
    expect(rateLimitMessage(1)).toBe('Please wait 1 second before trying again.');
  });

  it('falls back to a generic message when Retry-After is 0 or missing', () => {
    expect(rateLimitMessage(0)).toBe('Please slow down and try again shortly.');
  });
});
