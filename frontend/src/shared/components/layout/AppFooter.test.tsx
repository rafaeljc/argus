import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';

import { AppFooter } from './AppFooter';

describe('AppFooter', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2027-03-15T12:00:00Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('renders a contentinfo landmark with the current year', () => {
    render(<AppFooter />);
    const footer = screen.getByRole('contentinfo');
    expect(footer).toBeInTheDocument();
    expect(footer).toHaveTextContent('2027');
    expect(footer).toHaveTextContent(/argus/i);
  });
});
