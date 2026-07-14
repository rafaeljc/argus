import { useCallback, useEffect, useState } from 'react';
import { NavLink } from 'react-router-dom';

import { useAuthStore } from '../../hooks/useAuthStore';
import { visibleNavItems, type NavItem } from '../../routing/nav';

const DESKTOP_LINK_BASE =
  'rounded-md px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100 hover:text-slate-900 aria-[current=page]:bg-brand/10 aria-[current=page]:text-brand';

const DRAWER_LINK_BASE =
  'block rounded-md px-3 py-2 text-base font-medium text-slate-700 hover:bg-slate-100 hover:text-slate-900 aria-[current=page]:bg-brand/10 aria-[current=page]:text-brand';

function renderLinks(items: readonly NavItem[], className: string, onNavigate?: () => void) {
  return items.map((item) => (
    <NavLink key={item.to} to={item.to} className={className} onClick={onNavigate}>
      {item.label}
    </NavLink>
  ));
}

export function AppNav() {
  const user = useAuthStore((state) => state.user);
  const status = useAuthStore((state) => state.status);
  const [drawerOpen, setDrawerOpen] = useState(false);

  const items = visibleNavItems(user, status);
  const openDrawer = useCallback(() => setDrawerOpen(true), []);
  const closeDrawer = useCallback(() => setDrawerOpen(false), []);

  useEffect(() => {
    if (!drawerOpen) return;
    const handleKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setDrawerOpen(false);
    };
    document.addEventListener('keydown', handleKey);
    return () => document.removeEventListener('keydown', handleKey);
  }, [drawerOpen]);

  return (
    <nav aria-label="Main" className="flex flex-1 items-center justify-end">
      <div data-testid="app-nav-desktop" className="hidden items-center gap-1 md:flex">
        {renderLinks(items, DESKTOP_LINK_BASE)}
      </div>

      <button
        type="button"
        onClick={openDrawer}
        aria-expanded={drawerOpen}
        aria-controls="app-nav-drawer"
        aria-label="Open main navigation"
        className="inline-flex items-center justify-center rounded-md p-2 text-slate-700 hover:bg-slate-100 md:hidden"
      >
        <HamburgerIcon />
      </button>

      {drawerOpen && (
        <div
          role="dialog"
          aria-modal="true"
          aria-label="Main navigation"
          id="app-nav-drawer"
          className="fixed inset-0 z-50 md:hidden"
        >
          <div aria-hidden className="absolute inset-0 bg-slate-900/40" onClick={closeDrawer} />
          <div className="absolute inset-y-0 right-0 flex w-72 max-w-full flex-col bg-white shadow-xl">
            <div className="flex items-center justify-between border-b border-slate-200 px-4 py-3">
              <span className="text-base font-semibold text-slate-900">Menu</span>
              <button
                type="button"
                onClick={closeDrawer}
                aria-label="Close main navigation"
                className="inline-flex items-center justify-center rounded-md p-2 text-slate-700 hover:bg-slate-100"
              >
                <CloseIcon />
              </button>
            </div>
            <div className="flex flex-col gap-1 p-4">
              {renderLinks(items, DRAWER_LINK_BASE, closeDrawer)}
            </div>
          </div>
        </div>
      )}
    </nav>
  );
}

function HamburgerIcon() {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      className="h-6 w-6"
    >
      <path strokeLinecap="round" d="M4 6h16M4 12h16M4 18h16" />
    </svg>
  );
}

function CloseIcon() {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      className="h-6 w-6"
    >
      <path strokeLinecap="round" d="M6 6l12 12M18 6L6 18" />
    </svg>
  );
}
