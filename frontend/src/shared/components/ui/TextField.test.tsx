import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { axe } from 'jest-axe';

import { TextField } from './TextField';

describe('TextField', () => {
  it('associates the label with the input', () => {
    render(<TextField label="Email" name="email" />);
    const input = screen.getByLabelText('Email');
    expect(input).toBeInTheDocument();
    expect(input.tagName).toBe('INPUT');
  });

  it('renders the hint text with aria-describedby wiring', () => {
    render(<TextField label="Email" name="email" hint="We never share your email." />);
    const input = screen.getByLabelText('Email');
    const describedBy = input.getAttribute('aria-describedby');
    expect(describedBy).toBeTruthy();
    const hint = document.getElementById(describedBy as string);
    expect(hint).toHaveTextContent(/never share/i);
  });

  it('marks aria-invalid and points aria-describedby at the error message', () => {
    render(<TextField label="Email" name="email" error="Email is required." />);
    const input = screen.getByLabelText('Email');
    expect(input).toHaveAttribute('aria-invalid', 'true');
    const describedBy = input.getAttribute('aria-describedby');
    expect(describedBy).toBeTruthy();
    expect(document.getElementById(describedBy as string)).toHaveTextContent(/required/i);
  });

  it('supports type="email"', () => {
    render(<TextField label="Email" name="email" type="email" />);
    expect(screen.getByLabelText('Email')).toHaveAttribute('type', 'email');
  });

  it('accepts typed input', async () => {
    const user = userEvent.setup();
    render(<TextField label="Email" name="email" />);
    const input = screen.getByLabelText<HTMLInputElement>('Email');
    await user.type(input, 'me@example.com');
    expect(input.value).toBe('me@example.com');
  });

  it('has no a11y violations', async () => {
    const { container } = render(
      <TextField
        label="Email"
        name="email"
        hint="We never share your email."
        error="Email is required."
        required
      />,
    );
    expect(await axe(container)).toHaveNoViolations();
  });
});
