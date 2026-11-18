import type { TransferListQuery } from './transfers';

export const queryKeys = {
  accounts: ['accounts'] as const,
  limits: ['limits'] as const,
  statistics: ['statistics'] as const,
  transfers: ['transfers'] as const,
  transferList: (query: TransferListQuery) => ['transfers', 'list', query] as const,
  transfer: (id: number) => ['transfers', 'detail', id] as const,
};
