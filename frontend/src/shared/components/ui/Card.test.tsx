import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { axe } from 'jest-axe';

import { Card } from './Card';

describe('Card', () => {
  it('renders children', () => {
    render(
      <Card>
        <p>Body copy</p>
      </Card>,
    );
    expect(screen.getByText('Body copy')).toBeInTheDocument();
  });

  it('merges caller className after defaults', () => {
    render(<Card className="bg-slate-50">content</Card>);
    const card = screen.getByText('content').closest('div');
    expect(card).toHaveClass('rounded-lg', 'bg-slate-50');
  });

  it('has no a11y violations', async () => {
    const { container } = render(
      <Card>
        <p>Body copy</p>
      </Card>,
    );
    expect(await axe(container)).toHaveNoViolations();
  });
});
