import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { axe } from 'jest-axe';

import type { PaginationLinks, PaginationMeta } from '../../types/envelopes';
import { Pagination } from './Pagination';

function buildMeta(overrides: Partial<PaginationMeta> = {}): PaginationMeta {
  return { total: 675, page: 6, per_page: 25, total_pages: 27, ...overrides };
}

function buildLinks(overrides: Partial<PaginationLinks> = {}): PaginationLinks {
  return {
    self: '/things?page=6',
    prev: '/things?page=5',
    next: '/things?page=7',
    last: '/things?page=27',
    ...overrides,
  };
}

function getNav(): HTMLElement {
  return screen.getByRole('navigation', { name: /pagination/i });
}

describe('Pagination', () => {
  it('exposes a nav landmark labelled "Pagination"', () => {
    render(<Pagination meta={buildMeta()} links={buildLinks()} onPageChange={vi.fn()} />);
    expect(getNav()).toBeInTheDocument();
  });

  it('marks the current page with aria-current="page"', () => {
    render(<Pagination meta={buildMeta()} links={buildLinks()} onPageChange={vi.fn()} />);
    const current = within(getNav()).getByRole('button', { name: /^page 6$/i });
    expect(current).toHaveAttribute('aria-current', 'page');
  });

  it('renders a compact window with ellipses around the current page for large ranges', () => {
    render(<Pagination meta={buildMeta()} links={buildLinks()} onPageChange={vi.fn()} />);
    const nav = getNav();
    for (const label of [/^page 1$/i, /^page 5$/i, /^page 6$/i, /^page 7$/i, /^page 27$/i]) {
      expect(within(nav).getByRole('button', { name: label })).toBeInTheDocument();
    }
    expect(within(nav).queryByRole('button', { name: /^page 2$/i })).not.toBeInTheDocument();
    expect(within(nav).queryByRole('button', { name: /^page 26$/i })).not.toBeInTheDocument();
    expect(within(nav).getAllByText('…')).toHaveLength(2);
  });

  it('shows every page pill and no ellipsis when the range fits without collapse', () => {
    render(
      <Pagination
        meta={buildMeta({ page: 3, total_pages: 5, total: 125 })}
        links={buildLinks({ prev: '/x?page=2', next: '/x?page=4', last: '/x?page=5' })}
        onPageChange={vi.fn()}
      />,
    );
    const nav = getNav();
    for (const n of [1, 2, 3, 4, 5]) {
      expect(
        within(nav).getByRole('button', { name: new RegExp(`^page ${n}$`, 'i') }),
      ).toBeInTheDocument();
    }
    expect(within(nav).queryByText('…')).not.toBeInTheDocument();
  });

  it('renders a mobile status region with the current page and total', () => {
    render(<Pagination meta={buildMeta()} links={buildLinks()} onPageChange={vi.fn()} />);
    expect(screen.getByText(/page 6 of 27/i)).toBeInTheDocument();
  });

  it('disables "Previous" when links.prev is null', () => {
    render(
      <Pagination
        meta={buildMeta({ page: 1 })}
        links={buildLinks({ prev: null, self: '/x?page=1' })}
        onPageChange={vi.fn()}
      />,
    );
    expect(screen.getByRole('button', { name: /previous page/i })).toBeDisabled();
    expect(screen.getByRole('button', { name: /next page/i })).not.toBeDisabled();
  });

  it('disables "Next" when links.next is null', () => {
    render(
      <Pagination
        meta={buildMeta({ page: 27 })}
        links={buildLinks({ next: null, self: '/x?page=27' })}
        onPageChange={vi.fn()}
      />,
    );
    expect(screen.getByRole('button', { name: /next page/i })).toBeDisabled();
    expect(screen.getByRole('button', { name: /previous page/i })).not.toBeDisabled();
  });

  it('calls onPageChange with page + 1 when Next is clicked', async () => {
    const user = userEvent.setup();
    const onPageChange = vi.fn();
    render(<Pagination meta={buildMeta()} links={buildLinks()} onPageChange={onPageChange} />);
    await user.click(screen.getByRole('button', { name: /next page/i }));
    expect(onPageChange).toHaveBeenCalledExactlyOnceWith(7);
  });

  it('calls onPageChange with page - 1 when Previous is clicked', async () => {
    const user = userEvent.setup();
    const onPageChange = vi.fn();
    render(<Pagination meta={buildMeta()} links={buildLinks()} onPageChange={onPageChange} />);
    await user.click(screen.getByRole('button', { name: /previous page/i }));
    expect(onPageChange).toHaveBeenCalledExactlyOnceWith(5);
  });

  it('calls onPageChange with the clicked page number', async () => {
    const user = userEvent.setup();
    const onPageChange = vi.fn();
    render(<Pagination meta={buildMeta()} links={buildLinks()} onPageChange={onPageChange} />);
    await user.click(within(getNav()).getByRole('button', { name: /^page 1$/i }));
    expect(onPageChange).toHaveBeenCalledExactlyOnceWith(1);
  });

  it('does not fire onPageChange when the current page pill is clicked', async () => {
    const user = userEvent.setup();
    const onPageChange = vi.fn();
    render(<Pagination meta={buildMeta()} links={buildLinks()} onPageChange={onPageChange} />);
    await user.click(within(getNav()).getByRole('button', { name: /^page 6$/i }));
    expect(onPageChange).not.toHaveBeenCalled();
  });

  it('disables every control while isLoading', () => {
    render(
      <Pagination meta={buildMeta()} links={buildLinks()} onPageChange={vi.fn()} isLoading />,
    );
    const buttons = within(getNav()).getAllByRole('button');
    for (const button of buttons) {
      expect(button).toBeDisabled();
    }
  });

  it('renders nothing when total_pages <= 1', () => {
    const { container } = render(
      <Pagination
        meta={buildMeta({ page: 1, total_pages: 1, total: 10 })}
        links={buildLinks({ prev: null, next: null, self: '/x?page=1', last: '/x?page=1' })}
        onPageChange={vi.fn()}
      />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('has no a11y violations on the first page', async () => {
    const { container } = render(
      <Pagination
        meta={buildMeta({ page: 1 })}
        links={buildLinks({ prev: null, self: '/x?page=1' })}
        onPageChange={vi.fn()}
      />,
    );
    expect(await axe(container)).toHaveNoViolations();
  });

  it('has no a11y violations on a middle page', async () => {
    const { container } = render(
      <Pagination meta={buildMeta()} links={buildLinks()} onPageChange={vi.fn()} />,
    );
    expect(await axe(container)).toHaveNoViolations();
  });

  it('has no a11y violations on the last page', async () => {
    const { container } = render(
      <Pagination
        meta={buildMeta({ page: 27 })}
        links={buildLinks({ next: null, self: '/x?page=27' })}
        onPageChange={vi.fn()}
      />,
    );
    expect(await axe(container)).toHaveNoViolations();
  });
});
