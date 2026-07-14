import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError, RateLimitedError } from '../api/errors';
import { resetToastStoreForTest, useToastStore } from './useToastStore';
import { useForm } from './useForm';

interface LoginValues {
  email: string;
  password: string;
}

const INITIAL: LoginValues = { email: '', password: '' };

describe('useForm', () => {
  beforeEach(() => {
    resetToastStoreForTest();
  });

  afterEach(() => {
    resetToastStoreForTest();
  });

  it('exposes the initial values and a clean initial error state', () => {
    const { result } = renderHook(() =>
      useForm<LoginValues>({ initialValues: INITIAL, onSubmit: vi.fn() }),
    );

    expect(result.current.values).toEqual(INITIAL);
    expect(result.current.fieldErrors).toEqual({});
    expect(result.current.formError).toBeNull();
    expect(result.current.isSubmitting).toBe(false);
  });

  it('setValue updates a single field immutably', () => {
    const { result } = renderHook(() =>
      useForm<LoginValues>({ initialValues: INITIAL, onSubmit: vi.fn() }),
    );

    act(() => {
      result.current.setValue('email', 'me@example.com');
    });

    expect(result.current.values).toEqual({ email: 'me@example.com', password: '' });
    expect(result.current.values).not.toBe(INITIAL);
  });

  it('handleChange updates the field and clears its previous error', () => {
    const { result } = renderHook(() =>
      useForm<LoginValues>({ initialValues: INITIAL, onSubmit: vi.fn() }),
    );

    act(() => {
      result.current.setFieldErrors({ email: 'required' });
    });
    expect(result.current.fieldErrors.email).toBe('required');

    act(() => {
      const event = {
        target: { name: 'email', value: 'a@b.c' },
      } as unknown as React.ChangeEvent<HTMLInputElement>;
      result.current.handleChange('email')(event);
    });

    expect(result.current.values.email).toBe('a@b.c');
    expect(result.current.fieldErrors.email).toBeUndefined();
  });

  it('handleSubmit resolves happy path and passes current values to onSubmit', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const { result } = renderHook(() => useForm<LoginValues>({ initialValues: INITIAL, onSubmit }));

    act(() => {
      result.current.setValue('email', 'me@example.com');
      result.current.setValue('password', 'hunter2');
    });

    await act(async () => {
      await result.current.handleSubmit();
    });

    expect(onSubmit).toHaveBeenCalledWith({ email: 'me@example.com', password: 'hunter2' });
    expect(result.current.isSubmitting).toBe(false);
    expect(result.current.fieldErrors).toEqual({});
    expect(result.current.formError).toBeNull();
  });

  it('handleSubmit calls preventDefault on the passed FormEvent', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const preventDefault = vi.fn();
    const { result } = renderHook(() => useForm<LoginValues>({ initialValues: INITIAL, onSubmit }));

    await act(async () => {
      await result.current.handleSubmit({
        preventDefault,
      } as unknown as React.SubmitEvent<HTMLFormElement>);
    });

    expect(preventDefault).toHaveBeenCalledTimes(1);
  });

  it('maps ApiError.details[] to fieldErrors on matching keys', async () => {
    const onSubmit = vi.fn().mockRejectedValue(
      new ApiError({
        status: 422,
        code: 'VALIDATION_ERROR',
        message: 'invalid input',
        details: [
          { field: 'email', code: 'INVALID_FORMAT', message: 'Bad email.' },
          { field: 'password', code: 'TOO_SHORT', message: 'Too short.' },
        ],
      }),
    );
    const { result } = renderHook(() => useForm<LoginValues>({ initialValues: INITIAL, onSubmit }));

    await act(async () => {
      await result.current.handleSubmit();
    });

    expect(result.current.fieldErrors).toEqual({
      email: 'Bad email.',
      password: 'Too short.',
    });
    expect(result.current.formError).toBeNull();
    expect(result.current.isSubmitting).toBe(false);
  });

  it('keeps the first message when the same field appears twice in details', async () => {
    const onSubmit = vi.fn().mockRejectedValue(
      new ApiError({
        status: 422,
        code: 'VALIDATION_ERROR',
        message: 'invalid',
        details: [
          { field: 'email', code: 'INVALID_FORMAT', message: 'First.' },
          { field: 'email', code: 'DUPLICATE', message: 'Second.' },
        ],
      }),
    );
    const { result } = renderHook(() => useForm<LoginValues>({ initialValues: INITIAL, onSubmit }));

    await act(async () => {
      await result.current.handleSubmit();
    });

    expect(result.current.fieldErrors.email).toBe('First.');
  });

  it('collects details for unknown fields into formError so nothing is silently lost', async () => {
    const onSubmit = vi.fn().mockRejectedValue(
      new ApiError({
        status: 422,
        code: 'VALIDATION_ERROR',
        message: 'invalid',
        details: [
          { field: 'email', code: 'INVALID_FORMAT', message: 'Bad email.' },
          { field: 'not_a_field', code: 'X', message: 'Extra.' },
        ],
      }),
    );
    const { result } = renderHook(() => useForm<LoginValues>({ initialValues: INITIAL, onSubmit }));

    await act(async () => {
      await result.current.handleSubmit();
    });

    expect(result.current.fieldErrors).toEqual({ email: 'Bad email.' });
    expect(result.current.formError).toBe('Extra.');
  });

  it('surfaces ApiError without details as formError and a toast', async () => {
    const onSubmit = vi.fn().mockRejectedValue(
      new ApiError({
        status: 409,
        code: 'CONFLICT',
        message: 'Rule already exists.',
      }),
    );
    const { result } = renderHook(() => useForm<LoginValues>({ initialValues: INITIAL, onSubmit }));

    await act(async () => {
      await result.current.handleSubmit();
    });

    expect(result.current.formError).toBe('Rule already exists.');
    expect(result.current.fieldErrors).toEqual({});
    expect(useToastStore.getState().toasts).toHaveLength(1);
    expect(useToastStore.getState().toasts[0]?.message).toBe('Rule already exists.');
    expect(useToastStore.getState().toasts[0]?.variant).toBe('error');
  });

  it('swallows RateLimitedError so the global handler owns it (no local surface)', async () => {
    const rateErr = new RateLimitedError({
      message: 'Rate limited',
      retryAfterSeconds: 30,
    });
    const onSubmit = vi.fn().mockRejectedValue(rateErr);
    const { result } = renderHook(() => useForm<LoginValues>({ initialValues: INITIAL, onSubmit }));

    await act(async () => {
      await expect(result.current.handleSubmit()).resolves.toBeUndefined();
    });

    expect(result.current.formError).toBeNull();
    expect(result.current.fieldErrors).toEqual({});
    expect(result.current.isSubmitting).toBe(false);
    expect(useToastStore.getState().toasts).toHaveLength(0);
  });

  it('surfaces unknown errors as a generic formError plus toast', async () => {
    const onSubmit = vi.fn().mockRejectedValue(new Error('network down'));
    const { result } = renderHook(() => useForm<LoginValues>({ initialValues: INITIAL, onSubmit }));

    await act(async () => {
      await result.current.handleSubmit();
    });

    expect(result.current.formError).toBe('Something went wrong. Please try again.');
    expect(useToastStore.getState().toasts).toHaveLength(1);
    expect(useToastStore.getState().toasts[0]?.variant).toBe('error');
  });

  it('marks isSubmitting true during onSubmit and false afterwards', async () => {
    let resolveOnSubmit: () => void = () => {};
    const onSubmit = vi.fn().mockImplementation(
      () =>
        new Promise<void>((resolve) => {
          resolveOnSubmit = resolve;
        }),
    );
    const { result } = renderHook(() => useForm<LoginValues>({ initialValues: INITIAL, onSubmit }));

    let submitPromise: Promise<void>;
    act(() => {
      submitPromise = result.current.handleSubmit();
    });
    expect(result.current.isSubmitting).toBe(true);

    await act(async () => {
      resolveOnSubmit();
      await submitPromise;
    });
    expect(result.current.isSubmitting).toBe(false);
  });

  it('reset restores initial values and clears errors', async () => {
    const onSubmit = vi.fn().mockRejectedValue(
      new ApiError({
        status: 422,
        code: 'VALIDATION_ERROR',
        message: 'invalid',
        details: [{ field: 'email', code: 'X', message: 'Bad.' }],
      }),
    );
    const { result } = renderHook(() => useForm<LoginValues>({ initialValues: INITIAL, onSubmit }));

    act(() => {
      result.current.setValue('email', 'me@example.com');
    });
    await act(async () => {
      await result.current.handleSubmit();
    });
    expect(result.current.fieldErrors.email).toBe('Bad.');

    act(() => {
      result.current.reset();
    });

    expect(result.current.values).toEqual(INITIAL);
    expect(result.current.fieldErrors).toEqual({});
    expect(result.current.formError).toBeNull();
  });

  it('reset can seed the form with new values', () => {
    const { result } = renderHook(() =>
      useForm<LoginValues>({ initialValues: INITIAL, onSubmit: vi.fn() }),
    );

    act(() => {
      result.current.reset({ email: 'seed@example.com', password: 'seeded' });
    });

    expect(result.current.values).toEqual({ email: 'seed@example.com', password: 'seeded' });
  });
});
