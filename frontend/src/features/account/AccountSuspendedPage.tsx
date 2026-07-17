import { useNavigate } from 'react-router-dom';

import { PageContainer } from '../../shared/components/layout/PageContainer';
import { Button } from '../../shared/components/ui/Button';
import { Card } from '../../shared/components/ui/Card';

export function AccountSuspendedPage() {
  const navigate = useNavigate();

  return (
    <PageContainer>
      <div className="mx-auto w-full max-w-md">
        <Card>
          <h1 className="text-2xl font-semibold text-slate-900">Account suspended</h1>
          <p className="mt-1 text-sm text-slate-600">
            Your account access has been restricted. If you believe this is a mistake, contact an
            administrator.
          </p>
          <div className="mt-6">
            <Button type="button" variant="danger" onClick={() => navigate('/logout')}>
              Log out
            </Button>
          </div>
        </Card>
      </div>
    </PageContainer>
  );
}
