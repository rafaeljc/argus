import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { axe } from 'jest-axe';

import { DateField } from './DateField';

describe('DateField', () => {
  it('renders as a native date input', () => {
    render(<DateField label="Trade date" name="trade_date" />);
    const input = screen.getByLabelText<HTMLInputElement>('Trade date');
    expect(input).toHaveAttribute('type', 'date');
  });

  it('accepts an ISO date value', async () => {
    const user = userEvent.setup();
    render(<DateField label="Trade date" name="trade_date" />);
    const input = screen.getByLabelText<HTMLInputElement>('Trade date');
    await user.type(input, '2026-03-15');
    expect(input.value).toBe('2026-03-15');
  });

  it('marks aria-invalid when error is provided', () => {
    render(<DateField label="Trade date" name="trade_date" error="Cannot be in the future." />);
    expect(screen.getByLabelText('Trade date')).toHaveAttribute('aria-invalid', 'true');
  });

  it('passes through native constraints such as max', () => {
    render(<DateField label="Trade date" name="trade_date" max="2026-07-27" />);
    expect(screen.getByLabelText('Trade date')).toHaveAttribute('max', '2026-07-27');
  });

  it('has no a11y violations', async () => {
    const { container } = render(
      <DateField label="Trade date" name="trade_date" hint="No future dates." required />,
    );
    expect(await axe(container)).toHaveNoViolations();
  });
});
