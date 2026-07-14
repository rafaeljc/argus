import { Outlet } from 'react-router-dom';

import { AppFooter } from './AppFooter';
import { AppHeader } from './AppHeader';
import { PageContainer } from './PageContainer';

export function AppLayout() {
  return (
    <div className="flex min-h-screen flex-col bg-slate-50 text-slate-900">
      <AppHeader />
      <main className="flex-1">
        <PageContainer>
          <Outlet />
        </PageContainer>
      </main>
      <AppFooter />
    </div>
  );
}
