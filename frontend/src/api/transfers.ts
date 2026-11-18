import type { CreateTransferRequest, Page, Transfer, TransferDetail, TransferListItem } from '../types/api';
import { request } from './client';

export interface CreateTransferResult {
  transfer: Transfer;
  /** true when the server answered with Idempotent-Replayed: true (already processed earlier). */
  replayed: boolean;
}

export async function createTransfer(
  body: CreateTransferRequest,
  idempotencyKey: string,
): Promise<CreateTransferResult> {
  const { data, headers } = await request<Transfer>('/transfers', {
    method: 'POST',
    body,
    headers: { 'Idempotency-Key': idempotencyKey },
  });
  return { transfer: data, replayed: headers.get('Idempotent-Replayed') === 'true' };
}

export async function getTransfer(id: number): Promise<TransferDetail> {
  return (await request<TransferDetail>(`/transfers/${id}`)).data;
}

export interface TransferListQuery {
  page: number;
  size: number;
  startDate?: string; // yyyy-MM-dd
  endDate?: string; // yyyy-MM-dd
}

export async function listTransfers(query: TransferListQuery): Promise<Page<TransferListItem>> {
  return (
    await request<Page<TransferListItem>>('/transfers', {
      query: { ...query, sort: 'createdAt,desc' },
    })
  ).data;
}
