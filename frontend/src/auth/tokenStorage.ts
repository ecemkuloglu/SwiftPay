/**
 * Access token in memory + sessionStorage (never localStorage): it survives a reload within the same tab and
 * is gone when the tab closes; other tabs do not share it.
 */
const KEY = 'swiftpay.session';

interface StoredSession {
  token: string;
  expiresAt: number; // epoch millis
}

let current: StoredSession | null = read();

function read(): StoredSession | null {
  try {
    const raw = sessionStorage.getItem(KEY);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as StoredSession;
    return typeof parsed.token === 'string' && parsed.expiresAt > Date.now() ? parsed : null;
  } catch {
    return null;
  }
}

export const tokenStorage = {
  get(): string | null {
    if (current && current.expiresAt <= Date.now()) {
      tokenStorage.clear();
    }
    return current?.token ?? null;
  },

  set(token: string, expiresInSeconds: number): void {
    current = { token, expiresAt: Date.now() + expiresInSeconds * 1000 };
    try {
      sessionStorage.setItem(KEY, JSON.stringify(current));
    } catch {
      // storage unavailable (private mode): the in-memory copy still works for this page
    }
  },

  clear(): void {
    current = null;
    try {
      sessionStorage.removeItem(KEY);
    } catch {
      // ignore
    }
  },

  /** Tests only: re-read sessionStorage. */
  reload(): void {
    current = read();
  },
};
