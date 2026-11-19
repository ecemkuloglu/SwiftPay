import type { Account } from '../types/api';
import { formatMoney } from '../utils/money';

export function AccountCard({ account }: { account: Account }) {
  return (
    <article className="card account" aria-label={`Account ${account.maskedIban}`}>
      <p className="name">{account.currency} account</p>
      <p className="bal">{formatMoney(account.balance, account.currency)}</p>
      <p className="iban mono">{account.maskedIban}</p>
    </article>
  );
}
