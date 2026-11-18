import { http, HttpResponse } from 'msw';
import type {
  Account,
  ApiEnvelope,
  ErrorBody,
  Limits,
  MonthlyStat,
  Page,
  Transfer,
  TransferDetail,
  TransferListItem,
} from '../types/api';

// Seeded demo data from the README (ecem's accounts, ali's IBAN).
export const ECEM_MAIN_IBAN = 'TR620006100000000000001001';
export const ECEM_SECOND_IBAN = 'TR350006100000000000001002';
export const ALI_IBAN = 'TR690006200000000000002001';
export const ALI_SECOND_IBAN = 'TR350006200000000000003001';

export const accounts: Account[] = [
  {
    id: 1,
    iban: ECEM_MAIN_IBAN,
    maskedIban: 'TR62 **** **** **** **** 1001',
    currency: 'TRY',
    balance: '250000.00',
  },
  {
    id: 2,
    iban: ECEM_SECOND_IBAN,
    maskedIban: 'TR35 **** **** **** **** 1002',
    currency: 'TRY',
    balance: '10000.00',
  },
];

export const limits: Limits = {
  currency: 'TRY',
  perTransferLimit: '50000.00',
  daily: { limit: '100000.00', used: '35000.00', remaining: '65000.00' },
  monthly: { limit: '500000.00', used: '120000.00', remaining: '380000.00' },
};

export const statistics: MonthlyStat[] = [
  { month: '2026-05', totalTransferCount: 4, totalTransferredAmount: '12450.00' },
  { month: '2026-04', totalTransferCount: 2, totalTransferredAmount: '1800.00' },
  { month: '2026-03', totalTransferCount: 0, totalTransferredAmount: '0.00' },
  { month: '2026-02', totalTransferCount: 0, totalTransferredAmount: '0.00' },
  { month: '2026-01', totalTransferCount: 1, totalTransferredAmount: '0.01' },
  { month: '2025-12', totalTransferCount: 0, totalTransferredAmount: '0.00' },
];

export function transferFor(amount: string, id = 101): Transfer {
  return {
    id,
    sourceAccountId: 1,
    receiverIban: ALI_IBAN,
    maskedReceiverIban: 'TR69 **** **** **** **** 2001',
    amount,
    currency: 'TRY',
    description: 'Kira',
    status: 'COMPLETED',
    createdAt: '2026-05-14T09:30:00Z',
    completedAt: '2026-05-14T09:30:00Z',
  };
}

export const transferDetail: TransferDetail = {
  ...transferFor('1500.00'),
  sourceAccountMaskedIban: 'TR62 **** **** **** **** 1001',
};

export function listPage(
  items: TransferListItem[],
  page = 0,
  totalElements = items.length,
): Page<TransferListItem> {
  return { content: items, page, size: 10, totalElements, totalPages: Math.ceil(totalElements / 10) };
}

export function ok<T>(data: T, init?: ResponseInit) {
  const body: ApiEnvelope<T> = { data, traceId: 'trace-ok-0001', timestamp: '2026-05-14T09:30:00Z' };
  return HttpResponse.json(body, init);
}

export function apiError(
  status: number,
  code: string,
  extra: Partial<ErrorBody> = {},
  headers?: HeadersInit,
) {
  const body: ErrorBody = {
    code,
    message: `server message for ${code}`,
    traceId: `trace-${code.toLowerCase()}`,
    timestamp: '2026-05-14T09:30:00Z',
    ...extra,
  };
  return HttpResponse.json(body, { status, headers });
}

export const handlers = [
  http.get('*/api/accounts', () => ok(accounts)),
  http.get('*/api/accounts/limits', () => ok(limits)),
  http.get('*/api/statistics/monthly', () => ok(statistics)),
  http.get('*/api/transfers', () => ok(listPage([]))),
  http.get('*/api/transfers/:id', () => ok(transferDetail)),
  http.post('*/api/transfers', () => ok(transferFor('1500.00'), { status: 201 })),
  http.post('*/api/auth/login', () =>
    ok({ accessToken: 'jwt-test-token', tokenType: 'Bearer', expiresIn: 900 }),
  ),
];
