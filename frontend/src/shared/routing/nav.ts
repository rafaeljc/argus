import type { CurrentUser } from '../types/user';
import type { AuthStatus } from '../hooks/useAuthStore';

export type NavSection = 'primary' | 'admin' | 'auth';

export interface NavItem {
  label: string;
  to: string;
  section: NavSection;
}

const AUTH_ITEMS: readonly NavItem[] = [
  { label: 'Login', to: '/login', section: 'auth' },
  { label: 'Signup', to: '/signup', section: 'auth' },
] as const;

const PRIMARY_ITEMS: readonly NavItem[] = [
  { label: 'Portfolio', to: '/portfolio', section: 'primary' },
  { label: 'Transactions', to: '/transactions', section: 'primary' },
  { label: 'Alerts', to: '/alerts', section: 'primary' },
] as const;

const ADMIN_ITEMS: readonly NavItem[] = [
  { label: 'EOD pipeline', to: '/admin/eod-pipeline', section: 'admin' },
  { label: 'Audit log', to: '/admin/audit-log', section: 'admin' },
  { label: 'Users', to: '/admin/users', section: 'admin' },
] as const;

const LOGOUT_ITEM: NavItem = { label: 'Logout', to: '/logout', section: 'primary' };

const ACCOUNT_ITEMS: readonly NavItem[] = [
  { label: 'Account', to: '/account', section: 'primary' },
  LOGOUT_ITEM,
] as const;

export const NAV_ITEMS: readonly NavItem[] = [
  ...AUTH_ITEMS,
  ...PRIMARY_ITEMS,
  ...ADMIN_ITEMS,
  ...ACCOUNT_ITEMS,
] as const;

export function visibleNavItems(user: CurrentUser | null, status: AuthStatus): readonly NavItem[] {
  if (status !== 'authenticated' || user === null) {
    return AUTH_ITEMS;
  }
  if (!user.is_verified) {
    return ACCOUNT_ITEMS;
  }
  // Admins are staff, not investors: they get the admin surfaces instead of the portfolio
  // ones, and skip the personal Account page — Logout is their only account-level action.
  if (user.is_admin) {
    return [...ADMIN_ITEMS, LOGOUT_ITEM];
  }
  return [...PRIMARY_ITEMS, ...ACCOUNT_ITEMS];
}
