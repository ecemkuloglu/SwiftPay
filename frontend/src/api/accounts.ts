import type { Account, Limits } from '../types/api';
import { request } from './client';

export async function getAccounts(): Promise<Account[]> {
  return (await request<Account[]>('/accounts')).data;
}

export async function getLimits(): Promise<Limits> {
  return (await request<Limits>('/accounts/limits')).data;
}
