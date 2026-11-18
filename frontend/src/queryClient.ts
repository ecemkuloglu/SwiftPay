import { QueryClient } from '@tanstack/react-query';
import { ApiError } from './api/client';

/** Reads retry once on network / 5xx errors only; 4xx answers are final. Mutations are never retried. */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 15_000,
        retry: (failureCount, error) =>
          failureCount < 1 && error instanceof ApiError && (error.status === 0 || error.status >= 500),
      },
      mutations: { retry: false },
    },
  });
}
