const TR_IBAN = /^TR\d{24}$/;

/** Removes all whitespace and upper-cases ("tr33 0006 ..." -> "TR330006..."). */
export function normalizeIban(iban: string): string {
  return iban.replace(/\s+/g, '').toUpperCase();
}

/** TR + 24 digits with a valid ISO 13616 mod-97 checksum (same rule as the backend). */
export function isValidTrIban(iban: string): boolean {
  const value = normalizeIban(iban);
  if (!TR_IBAN.test(value)) {
    return false;
  }
  const rearranged = value.slice(4) + value.slice(0, 4);
  let remainder = 0;
  for (const char of rearranged) {
    const code = char.charCodeAt(0);
    const digits = code >= 65 && code <= 90 ? String(code - 55) : char; // A=10 ... Z=35
    for (const digit of digits) {
      remainder = (remainder * 10 + Number(digit)) % 97;
    }
  }
  return remainder === 1;
}

/** "TR330006100519786457841326" -> "TR33 **** **** **** **** 1326" (same format as the API). */
export function maskIban(iban: string): string {
  const value = normalizeIban(iban);
  if (value.length < 8) {
    return '****';
  }
  return `${value.slice(0, 4)} **** **** **** **** ${value.slice(-4)}`;
}

/** Groups an IBAN in blocks of 4 for readability (only used where the full IBAN may be shown). */
export function groupIban(iban: string): string {
  return normalizeIban(iban).replace(/(.{4})(?=.)/g, '$1 ');
}
