import { Link } from 'react-router-dom';

import { AppNav } from './AppNav';

export function AppHeader() {
  return (
    <header className="border-b border-slate-200 bg-white">
      <div className="mx-auto flex w-full max-w-6xl items-center gap-4 px-4 py-3 sm:px-6 lg:px-8">
        <Link to="/" className="text-lg font-semibold tracking-tight text-brand">
          Argus
        </Link>
        <AppNav />
      </div>
    </header>
  );
}
