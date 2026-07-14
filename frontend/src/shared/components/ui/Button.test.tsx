import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { axe } from 'jest-axe';

import { Button } from './Button';

describe('Button', () => {
  it('defaults to type="button" to avoid accidental submits', () => {
    render(<Button>Click</Button>);
    expect(screen.getByRole('button', { name: /click/i })).toHaveAttribute('type', 'button');
  });

  it('fires onClick when clicked', async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(<Button onClick={onClick}>Click</Button>);
    await user.click(screen.getByRole('button', { name: /click/i }));
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('renders a spinner and is disabled while loading', () => {
    render(<Button isLoading>Save</Button>);
    const button = screen.getByRole('button', { name: /save/i });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('aria-busy', 'true');
    expect(screen.getByRole('status', { name: /loading/i })).toBeInTheDocument();
  });

  it('does not fire onClick when disabled', async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(
      <Button disabled onClick={onClick}>
        Click
      </Button>,
    );
    await user.click(screen.getByRole('button', { name: /click/i }));
    expect(onClick).not.toHaveBeenCalled();
  });

  it('has no a11y violations for each variant', async () => {
    const { container } = render(
      <div>
        <Button variant="primary">Primary</Button>
        <Button variant="secondary">Secondary</Button>
        <Button variant="ghost">Ghost</Button>
        <Button variant="danger">Danger</Button>
        <Button isLoading>Loading</Button>
      </div>,
    );
    expect(await axe(container)).toHaveNoViolations();
  });
});
