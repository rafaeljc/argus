import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { axe } from 'jest-axe';

import { Skeleton } from './Skeleton';

describe('Skeleton', () => {
  it('renders a loading status region', () => {
    render(<Skeleton className="h-4 w-32" />);
    expect(screen.getByRole('status', { name: /loading/i })).toBeInTheDocument();
  });

  it('applies the caller-provided className for sizing', () => {
    render(<Skeleton className="h-10 w-full" />);
    expect(screen.getByRole('status')).toHaveClass('h-10', 'w-full');
  });

  it('has no a11y violations', async () => {
    const { container } = render(<Skeleton className="h-4 w-32" />);
    expect(await axe(container)).toHaveNoViolations();
  });
});
