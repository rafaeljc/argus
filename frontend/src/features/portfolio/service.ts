import { apiClient } from '../../shared/api/client';
import type { Portfolio, Snapshot, SnapshotRange } from './types';

export async function getPortfolio(): Promise<Portfolio> {
  const response = await apiClient.get<Portfolio>('/portfolio');
  return response.data;
}

export async function getSnapshots(range: SnapshotRange): Promise<Snapshot[]> {
  const response = await apiClient.get<Snapshot[]>('/portfolio/snapshots', {
    params: { range },
  });
  return response.data;
}
