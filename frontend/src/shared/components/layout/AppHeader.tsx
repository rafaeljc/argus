import { Link } from 'react-router-dom';

import { AppNav } from './AppNav';
import { ArgusLockup } from './ArgusLockup';

export function AppHeader() {
  return (
    <header className="border-b border-slate-200 bg-white">
      <div className="mx-auto flex w-full max-w-6xl items-center gap-4 px-4 py-3 sm:px-6 lg:px-8">
        <Link to="/" className="flex shrink-0 items-center rounded-sm">
          <ArgusLockup className="h-7 w-auto" />
        </Link>
        <AppNav />
      </div>
    </header>
  );
}
