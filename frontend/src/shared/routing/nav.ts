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
  { label: 'Transactions', to: '/transactions', section: 'primary' },
  { label: 'Portfolio', to: '/portfolio', section: 'primary' },
  { label: 'Alerts', to: '/alerts', section: 'primary' },
] as const;

const ADMIN_ITEMS: readonly NavItem[] = [
  { label: 'Admin', to: '/admin/users', section: 'admin' },
] as const;

const ACCOUNT_ITEMS: readonly NavItem[] = [
  { label: 'Account', to: '/account', section: 'primary' },
  { label: 'Logout', to: '/logout', section: 'primary' },
] as const;

export const NAV_ITEMS: readonly NavItem[] = [
  ...AUTH_ITEMS,
  ...PRIMARY_ITEMS,
  ...ADMIN_ITEMS,
  ...ACCOUNT_ITEMS,
] as const;

export function visibleNavItems(
  user: CurrentUser | null,
  status: AuthStatus,
): readonly NavItem[] {
  if (status !== 'authenticated' || user === null) {
    return AUTH_ITEMS;
  }
  if (!user.is_verified) {
    return ACCOUNT_ITEMS;
  }
  const items = [...PRIMARY_ITEMS];
  if (user.is_admin) items.push(...ADMIN_ITEMS);
  items.push(...ACCOUNT_ITEMS);
  return items;
}
