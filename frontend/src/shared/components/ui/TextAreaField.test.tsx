import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { axe } from 'jest-axe';

import { TextAreaField } from './TextAreaField';

describe('TextAreaField', () => {
  it('associates the label with the textarea', () => {
    render(<TextAreaField label="Reason" name="reason" />);
    const textarea = screen.getByLabelText('Reason');
    expect(textarea).toBeInTheDocument();
    expect(textarea.tagName).toBe('TEXTAREA');
  });

  it('renders the hint text with aria-describedby wiring', () => {
    render(
      <TextAreaField label="Reason" name="reason" hint="Optional context for the audit log." />,
    );
    const textarea = screen.getByLabelText('Reason');
    const describedBy = textarea.getAttribute('aria-describedby');
    expect(describedBy).toBeTruthy();
    expect(document.getElementById(describedBy as string)).toHaveTextContent(/optional context/i);
  });

  it('marks aria-invalid and points aria-describedby at the error message', () => {
    render(
      <TextAreaField
        label="Reason"
        name="reason"
        error="Reason must be 1000 characters or fewer."
      />,
    );
    const textarea = screen.getByLabelText('Reason');
    expect(textarea).toHaveAttribute('aria-invalid', 'true');
    const describedBy = textarea.getAttribute('aria-describedby');
    expect(describedBy).toBeTruthy();
    expect(document.getElementById(describedBy as string)).toHaveTextContent(/1000 characters/i);
  });

  it('hides the hint once an error is present', () => {
    render(
      <TextAreaField
        label="Reason"
        name="reason"
        hint="Optional context for the audit log."
        error="Reason must be 1000 characters or fewer."
      />,
    );
    expect(screen.queryByText(/optional context/i)).not.toBeInTheDocument();
  });

  it('accepts typed input', async () => {
    const user = userEvent.setup();
    render(<TextAreaField label="Reason" name="reason" />);
    const textarea = screen.getByLabelText<HTMLTextAreaElement>('Reason');
    await user.type(textarea, 'TOS violation report #4421');
    expect(textarea.value).toBe('TOS violation report #4421');
  });

  it('has no a11y violations', async () => {
    const { container } = render(
      <TextAreaField
        label="Reason"
        name="reason"
        hint="Optional context for the audit log."
        error="Reason must be 1000 characters or fewer."
      />,
    );
    expect(await axe(container)).toHaveNoViolations();
  });
});
