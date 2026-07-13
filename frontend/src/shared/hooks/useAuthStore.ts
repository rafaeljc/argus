import { create } from 'zustand';

import { fetchCurrentUser } from '../api/session';
import { ApiError, UnauthorizedError } from '../api/errors';
import type { CurrentUser } from '../types/user';

export type AuthStatus = 'idle' | 'loading' | 'authenticated' | 'anonymous';

export interface AuthState {
  user: CurrentUser | null;
  status: AuthStatus;
  error: string | null;
  setUser: (user: CurrentUser) => void;
  clearAuth: () => void;
  fetchUser: () => Promise<void>;
}

const INITIAL_STATE = {
  user: null,
  status: 'idle',
  error: null,
} as const satisfies Pick<AuthState, 'user' | 'status' | 'error'>;

function messageFor(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return 'Unexpected error';
}

export const useAuthStore = create<AuthState>((set, get) => ({
  ...INITIAL_STATE,

  setUser: (user) => {
    set({ user, status: 'authenticated', error: null });
  },

  clearAuth: () => {
    set({ user: null, status: 'anonymous', error: null });
  },

  fetchUser: async () => {
    if (get().status === 'loading') return;
    set({ status: 'loading', error: null });

    try {
      const user = await fetchCurrentUser();
      set({ user, status: 'authenticated', error: null });
    } catch (error: unknown) {
      if (error instanceof UnauthorizedError) {
        set({ user: null, status: 'anonymous', error: null });
        return;
      }
      set({ user: null, status: 'anonymous', error: messageFor(error) });
    }
  },
}));

export function resetAuthStoreForTest(): void {
  useAuthStore.setState({ ...INITIAL_STATE });
}
