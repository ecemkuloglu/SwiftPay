import { describe, expect, it } from 'vitest';
import { ALI_IBAN, ALI_SECOND_IBAN, ECEM_MAIN_IBAN, ECEM_SECOND_IBAN } from '../test/handlers';
import { groupIban, isValidTrIban, maskIban, normalizeIban } from './iban';

describe('isValidTrIban', () => {
  it.each([ECEM_MAIN_IBAN, ECEM_SECOND_IBAN, ALI_IBAN, ALI_SECOND_IBAN, 'TR330006100519786457841326'])(
    'accepts the seeded / spec IBAN %s',
    (iban) => {
      expect(isValidTrIban(iban)).toBe(true);
    },
  );

  it('accepts spaces and lower case', () => {
    expect(isValidTrIban('tr69 0006 2000 0000 0000 0020 01')).toBe(true);
  });

  it.each([
    'TR640006200000000000000002', // API_SPEC example with a wrong checksum
    'TR690006200000000000002002', // last digit changed
    'TR12345',
    'TR6900062000000000000020011', // 25 digits
    'DE89370400440532013000',
    'TR69000620000000000000200A',
    '',
  ])('rejects %j', (iban) => {
    expect(isValidTrIban(iban)).toBe(false);
  });
});

describe('IBAN formatting', () => {
  it('masks like the API', () => {
    expect(maskIban(ALI_IBAN)).toBe('TR69 **** **** **** **** 2001');
    expect(maskIban('TR1')).toBe('****');
  });

  it('normalizes and groups', () => {
    expect(normalizeIban(' tr69 0006 ')).toBe('TR690006');
    expect(groupIban(ALI_IBAN)).toBe('TR69 0006 2000 0000 0000 0020 01');
  });
});
