import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { axe } from 'jest-axe';

import { EmptyState } from './EmptyState';

describe('EmptyState', () => {
  it('renders title and description', () => {
    render(<EmptyState title="No holdings yet" description="Record a transaction to start." />);
    expect(screen.getByRole('heading', { name: /no holdings yet/i })).toBeInTheDocument();
    expect(screen.getByText(/record a transaction/i)).toBeInTheDocument();
  });

  it('renders an optional action slot', () => {
    render(
      <EmptyState
        title="No holdings yet"
        description="Record a transaction to start."
        action={<button type="button">Record transaction</button>}
      />,
    );
    expect(screen.getByRole('button', { name: /record transaction/i })).toBeInTheDocument();
  });

  it('has no a11y violations', async () => {
    const { container } = render(
      <EmptyState
        title="No holdings yet"
        description="Record a transaction to start."
        action={<button type="button">Record transaction</button>}
      />,
    );
    expect(await axe(container)).toHaveNoViolations();
  });
});
