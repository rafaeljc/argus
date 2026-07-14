import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { axe } from 'jest-axe';

import { NumberField } from './NumberField';

describe('NumberField', () => {
  it('renders as text input with decimal inputMode to preserve precision', () => {
    render(<NumberField label="Quantity" name="quantity" />);
    const input = screen.getByLabelText('Quantity');
    expect(input).toHaveAttribute('type', 'text');
    expect(input).toHaveAttribute('inputmode', 'decimal');
  });

  it('accepts decimal-string input without coercion', async () => {
    const user = userEvent.setup();
    render(<NumberField label="Quantity" name="quantity" />);
    const input = screen.getByLabelText<HTMLInputElement>('Quantity');
    await user.type(input, '123.456789');
    expect(input.value).toBe('123.456789');
  });

  it('marks aria-invalid when error is provided', () => {
    render(<NumberField label="Quantity" name="quantity" error="Must be greater than 0." />);
    expect(screen.getByLabelText('Quantity')).toHaveAttribute('aria-invalid', 'true');
  });

  it('has no a11y violations', async () => {
    const { container } = render(
      <NumberField label="Quantity" name="quantity" hint="Up to 6 decimal places." required />,
    );
    expect(await axe(container)).toHaveNoViolations();
  });
});
