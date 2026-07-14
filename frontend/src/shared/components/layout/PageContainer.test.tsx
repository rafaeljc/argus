import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';

import { PageContainer } from './PageContainer';

describe('PageContainer', () => {
  it('renders its children', () => {
    render(
      <PageContainer>
        <p>hello</p>
      </PageContainer>,
    );
    expect(screen.getByText('hello')).toBeInTheDocument();
  });

  it('constrains width and centers content', () => {
    render(
      <PageContainer>
        <span data-testid="child" />
      </PageContainer>,
    );
    const wrapper = screen.getByTestId('child').parentElement;
    expect(wrapper).not.toBeNull();
    expect(wrapper?.className).toMatch(/mx-auto/);
    expect(wrapper?.className).toMatch(/max-w-/);
  });
});
