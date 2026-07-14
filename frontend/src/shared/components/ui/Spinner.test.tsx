import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { axe } from 'jest-axe';

import { Spinner } from './Spinner';

describe('Spinner', () => {
  it('exposes a status role for assistive tech', () => {
    render(<Spinner />);
    expect(screen.getByRole('status', { name: /loading/i })).toBeInTheDocument();
  });

  it('accepts a size prop', () => {
    render(<Spinner size="lg" />);
    const svg = screen.getByRole('status').querySelector('svg');
    expect(svg).toHaveClass('h-8', 'w-8');
  });

  it('has no a11y violations', async () => {
    const { container } = render(<Spinner />);
    expect(await axe(container)).toHaveNoViolations();
  });
});
