import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { axe } from 'jest-axe';

import { PasswordField } from './PasswordField';

describe('PasswordField', () => {
  it('renders as type="password" by default', () => {
    render(<PasswordField label="Password" name="password" />);
    expect(screen.getByLabelText('Password')).toHaveAttribute('type', 'password');
  });

  it('toggles visibility when the show/hide button is clicked', async () => {
    const user = userEvent.setup();
    render(<PasswordField label="Password" name="password" />);
    const input = screen.getByLabelText('Password');
    const toggle = screen.getByRole('button', { name: /show password/i });

    await user.click(toggle);
    expect(input).toHaveAttribute('type', 'text');
    expect(screen.getByRole('button', { name: /hide password/i })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /hide password/i }));
    expect(input).toHaveAttribute('type', 'password');
  });

  it('surfaces error state with aria-invalid', () => {
    render(<PasswordField label="Password" name="password" error="Too short." />);
    expect(screen.getByLabelText('Password')).toHaveAttribute('aria-invalid', 'true');
  });

  it('has no a11y violations', async () => {
    const { container } = render(
      <PasswordField label="Password" name="password" hint="At least 8 characters." required />,
    );
    expect(await axe(container)).toHaveNoViolations();
  });
});
