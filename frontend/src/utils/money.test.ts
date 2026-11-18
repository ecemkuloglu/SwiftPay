import { describe, expect, it } from 'vitest';
import { formatMoney, parseAmountInput, toBarValue } from './money';

describe('formatMoney (tr-TR style, string based)', () => {
  it.each([
    ['12450.00', '12.450,00 TRY'],
    ['0.00', '0,00 TRY'],
    ['0.01', '0,01 TRY'],
    ['250000.00', '250.000,00 TRY'],
    ['1000000', '1.000.000,00 TRY'],
    ['99999999999999999.99', '99.999.999.999.999.999,99 TRY'], // beyond float precision, still exact
    ['1500.5', '1.500,50 TRY'],
    ['-5.00', '-5,00 TRY'],
  ])('%s -> %s', (input, expected) => {
    expect(formatMoney(input)).toBe(expected);
  });

  it('returns unexpected input unchanged instead of guessing', () => {
    expect(formatMoney('abc')).toBe('abc');
    expect(formatMoney('1e5')).toBe('1e5');
  });
});

describe('parseAmountInput', () => {
  it.each([
    ['1500', '1500.00'],
    ['1500,5', '1500.50'],
    ['1500.5', '1500.50'],
    ['1500,50', '1500.50'],
    [' 0,01 ', '0.01'],
    ['007', '7.00'],
    ['12345678901234567.99', '12345678901234567.99'], // 17 integer digits, exact
    ['10.', '10.00'],
  ])('accepts %s as %s', (input, expected) => {
    expect(parseAmountInput(input)).toEqual({ ok: true, value: expected });
  });

  it.each([
    ['', 'Enter an amount.'],
    ['0', 'The amount must be greater than 0.'],
    ['0,00', 'The amount must be greater than 0.'],
    ['-5', 'Use digits with an optional decimal separator, e.g. 1500,50.'],
    ['10.001', 'At most 2 decimal places.'],
    ['1.500,50', 'Use digits with an optional decimal separator, e.g. 1500,50.'], // no thousands separators
    ['1e3', 'Use digits with an optional decimal separator, e.g. 1500,50.'],
    ['abc', 'Use digits with an optional decimal separator, e.g. 1500,50.'],
    ['123456789012345678', 'The amount is too large.'], // 18 integer digits
  ])('rejects %j', (input, error) => {
    expect(parseAmountInput(input)).toEqual({ ok: false, error });
  });
});

describe('toBarValue (visual width only)', () => {
  it('turns money into a bar value and never returns NaN', () => {
    expect(toBarValue('35000.00')).toBe(35000);
    expect(toBarValue('oops')).toBe(0);
  });
});
