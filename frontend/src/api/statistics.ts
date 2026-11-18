import type { MonthlyStat } from '../types/api';
import { request } from './client';

export async function getMonthlyStatistics(): Promise<MonthlyStat[]> {
  return (await request<MonthlyStat[]>('/statistics/monthly')).data;
}
