import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { axe } from 'jest-axe';

import { SelectField } from './SelectField';

const OPTIONS = [
  { value: 'UP', label: 'Up' },
  { value: 'DOWN', label: 'Down' },
] as const;

describe('SelectField', () => {
  it('renders label + native select with the provided options', () => {
    render(<SelectField label="Direction" name="direction" options={OPTIONS} />);
    const select = screen.getByLabelText<HTMLSelectElement>('Direction');
    expect(select.tagName).toBe('SELECT');
    expect(screen.getByRole('option', { name: 'Up' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Down' })).toBeInTheDocument();
  });

  it('changes the selected value', async () => {
    const user = userEvent.setup();
    render(<SelectField label="Direction" name="direction" options={OPTIONS} defaultValue="UP" />);
    const select = screen.getByLabelText<HTMLSelectElement>('Direction');
    await user.selectOptions(select, 'DOWN');
    expect(select.value).toBe('DOWN');
  });

  it('renders a placeholder option when placeholder prop is provided', () => {
    render(
      <SelectField
        label="Direction"
        name="direction"
        options={OPTIONS}
        placeholder="Select a direction"
      />,
    );
    expect(screen.getByRole('option', { name: /select a direction/i })).toBeInTheDocument();
  });

  it('marks aria-invalid when error is provided', () => {
    render(
      <SelectField label="Direction" name="direction" options={OPTIONS} error="Choose one." />,
    );
    expect(screen.getByLabelText('Direction')).toHaveAttribute('aria-invalid', 'true');
  });

  it('has no a11y violations', async () => {
    const { container } = render(
      <SelectField
        label="Direction"
        name="direction"
        options={OPTIONS}
        hint="Which way should the price move?"
        required
      />,
    );
    expect(await axe(container)).toHaveNoViolations();
  });
});
