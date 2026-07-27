import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { axe } from 'jest-axe';

import { SegmentedField } from './SegmentedField';

const OPTIONS = [
  { value: 'BUY', label: 'Buy' },
  { value: 'SELL', label: 'Sell' },
] as const;

describe('SegmentedField', () => {
  it('renders a radiogroup with one radio per option', () => {
    render(
      <SegmentedField
        label="Operation"
        name="operation"
        options={OPTIONS}
        value="BUY"
        onChange={() => {}}
      />,
    );
    expect(screen.getByRole('radiogroup', { name: 'Operation' })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: 'Buy' })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: 'Sell' })).toBeInTheDocument();
  });

  it('marks the current value as checked', () => {
    render(
      <SegmentedField
        label="Operation"
        name="operation"
        options={OPTIONS}
        value="SELL"
        onChange={() => {}}
      />,
    );
    expect(screen.getByRole('radio', { name: 'Sell' })).toBeChecked();
    expect(screen.getByRole('radio', { name: 'Buy' })).not.toBeChecked();
  });

  it('calls onChange with the selected option value when a segment is clicked', async () => {
    const user = userEvent.setup();
    const handleChange = vi.fn();
    render(
      <SegmentedField
        label="Operation"
        name="operation"
        options={OPTIONS}
        value="BUY"
        onChange={handleChange}
      />,
    );
    await user.click(screen.getByRole('radio', { name: 'Sell' }));
    expect(handleChange).toHaveBeenCalledTimes(1);
    const event = handleChange.mock.calls[0]![0];
    expect(event.target.value).toBe('SELL');
    expect(event.target.name).toBe('operation');
  });

  it('marks aria-invalid on the radiogroup when error is provided', () => {
    render(
      <SegmentedField
        label="Operation"
        name="operation"
        options={OPTIONS}
        value="BUY"
        onChange={() => {}}
        error="Choose an operation."
      />,
    );
    expect(screen.getByRole('radiogroup', { name: 'Operation' })).toHaveAttribute(
      'aria-invalid',
      'true',
    );
    expect(screen.getByText('Choose an operation.')).toBeInTheDocument();
  });

  it('renders the hint when no error is present', () => {
    render(
      <SegmentedField
        label="Operation"
        name="operation"
        options={OPTIONS}
        value="BUY"
        onChange={() => {}}
        hint="Choose buy or sell."
      />,
    );
    expect(screen.getByText('Choose buy or sell.')).toBeInTheDocument();
  });

  it('has no a11y violations', async () => {
    const { container } = render(
      <SegmentedField
        label="Operation"
        name="operation"
        options={OPTIONS}
        value="BUY"
        onChange={() => {}}
        required
      />,
    );
    expect(await axe(container)).toHaveNoViolations();
  });
});
