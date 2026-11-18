import type { ApiEnvelope, ErrorBody, FieldError } from '../types/api';

/** Every failed call becomes an ApiError. A request that got no response at all has code NETWORK_ERROR. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly traceId: string | undefined;
  readonly fieldErrors: FieldError[];
  /** Seconds from the Retry-After header (429), if present. */
  readonly retryAfter: number | undefined;

  constructor(init: {
    status: number;
    code: string;
    message: string;
    traceId?: string | undefined;
    fieldErrors?: FieldError[] | undefined;
    retryAfter?: number | undefined;
  }) {
    super(init.message);
    this.name = 'ApiError';
    this.status = init.status;
    this.code = init.code;
    this.traceId = init.traceId;
    this.fieldErrors = init.fieldErrors ?? [];
    this.retryAfter = init.retryAfter;
  }
}

export interface ApiResult<T> {
  data: T;
  headers: Headers;
}

interface ClientConfig {
  baseUrl: string;
  getToken: () => string | null;
  onUnauthorized: () => void;
}

const config: ClientConfig = {
  baseUrl: import.meta.env.VITE_API_URL || '/api',
  getToken: () => null,
  onUnauthorized: () => undefined,
};

/** Wired once by the AuthProvider (token source and what to do on a 401). */
export function configureApiClient(partial: Partial<ClientConfig>): void {
  Object.assign(config, partial);
}

export interface RequestOptions {
  method?: 'GET' | 'POST';
  body?: unknown;
  headers?: Record<string, string>;
  query?: Record<string, string | number | undefined>;
  /** Login itself: no bearer token, and its 401 is a normal "wrong credentials" error, not "session expired". */
  anonymous?: boolean;
}

/** The single entry point for every API call. Never logs tokens, IBANs or bodies. */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<ApiResult<T>> {
  const url = new URL(config.baseUrl.replace(/\/$/, '') + path, window.location.origin);
  for (const [key, value] of Object.entries(options.query ?? {})) {
    if (value !== undefined && value !== '') {
      url.searchParams.set(key, String(value));
    }
  }

  const headers: Record<string, string> = { Accept: 'application/json', ...options.headers };
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  const token = options.anonymous ? null : config.getToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  let response: Response;
  try {
    response = await fetch(url, {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    });
  } catch {
    throw new ApiError({
      status: 0,
      code: 'NETWORK_ERROR',
      message: 'The server could not be reached. Check your connection and try again.',
    });
  }

  const payload: unknown = await readJson(response);
  if (!response.ok) {
    if (response.status === 401 && !options.anonymous) {
      config.onUnauthorized();
    }
    throw toApiError(response, payload);
  }
  return { data: (payload as ApiEnvelope<T>).data, headers: response.headers };
}

async function readJson(response: Response): Promise<unknown> {
  const text = await response.text();
  if (!text) {
    return null;
  }
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return null;
  }
}

function toApiError(response: Response, payload: unknown): ApiError {
  const body = isErrorBody(payload) ? payload : undefined;
  const retryAfter = Number(response.headers.get('Retry-After'));
  return new ApiError({
    status: response.status,
    code: body?.code ?? (response.status >= 500 ? 'INTERNAL_ERROR' : 'UNKNOWN_ERROR'),
    message: body?.message ?? `Request failed (HTTP ${response.status}).`,
    traceId: body?.traceId ?? response.headers.get('X-Trace-Id') ?? undefined,
    fieldErrors: body?.fieldErrors,
    retryAfter: Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : undefined,
  });
}

function isErrorBody(value: unknown): value is ErrorBody {
  return typeof value === 'object' && value !== null && typeof (value as ErrorBody).code === 'string';
}
