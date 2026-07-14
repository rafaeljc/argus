import { clsx } from 'clsx';

import type { PaginationLinks, PaginationMeta } from '../../types/envelopes';
import { Button } from './Button';

export interface PaginationProps {
  meta: PaginationMeta;
  links: PaginationLinks;
  onPageChange: (page: number) => void;
  isLoading?: boolean;
  className?: string;
}

type PageItem = { kind: 'page'; page: number } | { kind: 'ellipsis'; key: string };

function buildPageItems(currentPage: number, totalPages: number): PageItem[] {
  const anchors = new Set<number>([
    1,
    totalPages,
    currentPage - 1,
    currentPage,
    currentPage + 1,
  ]);
  const pages = [...anchors].filter((p) => p >= 1 && p <= totalPages).sort((a, b) => a - b);

  const pageItems: PageItem[] = [];
  pages.forEach((page, index) => {
    const previous = pages[index - 1];
    if (previous !== undefined && page - previous > 1) {
      pageItems.push({ kind: 'ellipsis', key: `ellipsis-${previous}-${page}` });
    }
    pageItems.push({ kind: 'page', page });
  });
  return pageItems;
}

export function Pagination({
  meta,
  links,
  onPageChange,
  isLoading = false,
  className,
}: PaginationProps) {
  if (meta.total_pages <= 1) {
    return null;
  }

  const canGoPrev = links.prev !== null && !isLoading;
  const canGoNext = links.next !== null && !isLoading;
  const pageItems = buildPageItems(meta.page, meta.total_pages);

  return (
    <nav
      aria-label="Pagination"
      className={clsx('flex items-center justify-between gap-4', className)}
    >
      <Button
        variant="secondary"
        size="sm"
        aria-label="Previous page"
        disabled={!canGoPrev}
        onClick={() => onPageChange(meta.page - 1)}
      >
        Previous
      </Button>

      <span aria-live="polite" className="text-sm text-slate-600 sm:hidden">
        Page {meta.page} of {meta.total_pages}
      </span>

      <ol className="hidden items-center gap-1 sm:flex" role="list">
        {pageItems.map((item) =>
          item.kind === 'ellipsis' ? (
            <li key={item.key} aria-hidden="true" className="px-2 text-sm text-slate-500">
              …
            </li>
          ) : (
            <li key={item.page}>
              <PagePill
                page={item.page}
                isCurrent={item.page === meta.page}
                isLoading={isLoading}
                onSelect={onPageChange}
              />
            </li>
          ),
        )}
      </ol>

      <Button
        variant="secondary"
        size="sm"
        aria-label="Next page"
        disabled={!canGoNext}
        onClick={() => onPageChange(meta.page + 1)}
      >
        Next
      </Button>
    </nav>
  );
}

interface PagePillProps {
  page: number;
  isCurrent: boolean;
  isLoading: boolean;
  onSelect: (page: number) => void;
}

function PagePill({ page, isCurrent, isLoading, onSelect }: PagePillProps) {
  return (
    <Button
      variant={isCurrent ? 'primary' : 'ghost'}
      size="sm"
      aria-label={`Page ${page}`}
      aria-current={isCurrent ? 'page' : undefined}
      disabled={isLoading || isCurrent}
      onClick={() => onSelect(page)}
    >
      {page}
    </Button>
  );
}
