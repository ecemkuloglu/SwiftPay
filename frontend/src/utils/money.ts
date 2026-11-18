import type { Money } from '../types/api';

const API_MONEY = /^(-?)(\d+)(?:\.(\d+))?$/;
const INPUT_AMOUNT = /^(\d+)(?:[.,](\d*))?$/;
export const MAX_INTEGER_DIGITS = 17;

/**
 * "12450.00" -> "12.450,00 TRY" (tr-TR style) using string operations only, so no value is ever turned into a
 * binary float. Unexpected input is returned unchanged rather than shown wrong.
 */
export function formatMoney(value: Money, currency = 'TRY'): string {
  const match = API_MONEY.exec(value.trim());
  if (!match) {
    return value;
  }
  const [, sign, integer = '0', fraction = ''] = match;
  const grouped = stripLeadingZeros(integer).replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  const decimals = (fraction + '00').slice(0, Math.max(2, fraction.length));
  return `${sign}${grouped},${decimals} ${currency}`;
}

export type AmountResult = { ok: true; value: Money } | { ok: false; error: string };

/**
 * Validates what the user typed and returns the canonical string to send: "1500" -> "1500.00",
 * "1500,5" -> "1500.50". One decimal separator ("." or ","), no thousands separators (so "1.500" can never be
 * misread), > 0, at most 2 decimals, at most 17 integer digits (mirrors the backend).
 */
export function parseAmountInput(input: string): AmountResult {
  const trimmed = input.trim();
  if (trimmed === '') {
    return { ok: false, error: 'Enter an amount.' };
  }
  const match = INPUT_AMOUNT.exec(trimmed);
  if (!match) {
    return { ok: false, error: 'Use digits with an optional decimal separator, e.g. 1500,50.' };
  }
  const integer = stripLeadingZeros(match[1] ?? '0');
  const fraction = match[2] ?? '';
  if (fraction.length > 2) {
    return { ok: false, error: 'At most 2 decimal places.' };
  }
  if (integer.length > MAX_INTEGER_DIGITS) {
    return { ok: false, error: 'The amount is too large.' };
  }
  const value = `${integer}.${fraction.padEnd(2, '0')}`;
  if (/^0\.00$/.test(value)) {
    return { ok: false, error: 'The amount must be greater than 0.' };
  }
  return { ok: true, value };
}

/**
 * The ONLY place a money string becomes a number: the visual fill of a progress bar. Never use it for display,
 * comparison or arithmetic.
 */
export function toBarValue(value: Money): number {
  const n = Number(value);
  return Number.isFinite(n) && n > 0 ? n : 0;
}

function stripLeadingZeros(digits: string): string {
  return digits.replace(/^0+(?=\d)/, '');
}
