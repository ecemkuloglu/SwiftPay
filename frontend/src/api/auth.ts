import type { LoginRequest, LoginResponse } from '../types/api';
import { request } from './client';

export async function login(credentials: LoginRequest): Promise<LoginResponse> {
  const { data } = await request<LoginResponse>('/auth/login', {
    method: 'POST',
    body: credentials,
    anonymous: true,
  });
  return data;
}
