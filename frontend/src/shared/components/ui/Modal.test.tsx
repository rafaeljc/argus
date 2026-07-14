import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { axe } from 'jest-axe';

import { Modal } from './Modal';

describe('Modal', () => {
  it('does not render when open is false', () => {
    render(
      <Modal open={false} onClose={() => {}} title="Delete account">
        <p>Body</p>
      </Modal>,
    );
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('renders a titled modal dialog when open', () => {
    render(
      <Modal open onClose={() => {}} title="Delete account">
        <p>Are you sure?</p>
      </Modal>,
    );
    const dialog = screen.getByRole('dialog', { name: /delete account/i });
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(screen.getByText(/are you sure/i)).toBeInTheDocument();
  });

  it('closes when the close button is clicked', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(
      <Modal open onClose={onClose} title="Delete account">
        <p>Body</p>
      </Modal>,
    );
    await user.click(screen.getByRole('button', { name: /close/i }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('closes on Escape', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(
      <Modal open onClose={onClose} title="Delete account">
        <p>Body</p>
      </Modal>,
    );
    await user.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('closes when the backdrop is clicked', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(
      <Modal open onClose={onClose} title="Delete account">
        <p>Body</p>
      </Modal>,
    );
    await user.click(screen.getByTestId('modal-backdrop'));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('has no a11y violations when open', async () => {
    const { container } = render(
      <Modal open onClose={() => {}} title="Delete account">
        <p>Body</p>
      </Modal>,
    );
    expect(await axe(container)).toHaveNoViolations();
  });
});
