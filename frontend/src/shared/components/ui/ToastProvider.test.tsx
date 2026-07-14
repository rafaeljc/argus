import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { axe } from 'jest-axe';

import { resetToastStoreForTest, useToastStore } from '../../hooks/useToastStore';
import { ToastProvider } from './ToastProvider';

describe('ToastProvider', () => {
  beforeEach(() => {
    resetToastStoreForTest();
  });

  afterEach(() => {
    resetToastStoreForTest();
    vi.useRealTimers();
  });

  it('renders the notifications region even when empty', () => {
    render(<ToastProvider />);

    const region = screen.getByRole('region', { name: /notifications/i });
    expect(region).toBeInTheDocument();
    expect(region).toHaveAttribute('aria-live', 'polite');
    expect(within(region).queryAllByRole('listitem')).toHaveLength(0);
  });

  it('renders a pushed info toast in the polite region', () => {
    render(<ToastProvider />);

    act(() => {
      useToastStore.getState().push({ variant: 'info', message: 'saved' });
    });

    const region = screen.getByRole('region', { name: /notifications/i });
    expect(within(region).getByText('saved')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('renders error toasts inside a role="alert" node so they are announced assertively', () => {
    render(<ToastProvider />);

    act(() => {
      useToastStore.getState().push({ variant: 'error', message: 'nope' });
    });

    const alert = screen.getByRole('alert');
    expect(alert).toHaveTextContent('nope');
  });

  it('dismisses a toast via its dismiss button', async () => {
    const user = userEvent.setup();
    render(<ToastProvider />);

    act(() => {
      useToastStore.getState().push({ variant: 'info', message: 'saved' });
    });
    expect(screen.getByText('saved')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /dismiss/i }));

    expect(screen.queryByText('saved')).toBeNull();
    expect(useToastStore.getState().toasts).toHaveLength(0);
  });

  it('auto-dismisses a toast after its durationMs elapses', () => {
    vi.useFakeTimers();
    render(<ToastProvider />);

    act(() => {
      useToastStore.getState().push({ variant: 'info', message: 'goes away', durationMs: 100 });
    });
    expect(screen.getByText('goes away')).toBeInTheDocument();

    act(() => {
      vi.advanceTimersByTime(100);
    });

    expect(screen.queryByText('goes away')).toBeNull();
    expect(useToastStore.getState().toasts).toHaveLength(0);
  });

  it('does not auto-dismiss a sticky toast (durationMs === null)', () => {
    vi.useFakeTimers();
    render(<ToastProvider />);

    act(() => {
      useToastStore.getState().push({ variant: 'error', message: 'stays', durationMs: null });
    });

    act(() => {
      vi.advanceTimersByTime(60_000);
    });

    expect(screen.getByText('stays')).toBeInTheDocument();
    expect(useToastStore.getState().toasts).toHaveLength(1);
  });

  it('has no a11y violations with a mixed queue', async () => {
    const { container } = render(<ToastProvider />);

    act(() => {
      useToastStore.getState().push({ variant: 'success', message: 'saved' });
      useToastStore.getState().push({ variant: 'error', message: 'server exploded' });
    });

    expect(await axe(container)).toHaveNoViolations();
  });
});
