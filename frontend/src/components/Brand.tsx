import { Link } from 'react-router';

/** Logo mark + name. Inline SVG uses presentation attributes only (no style attribute, CSP-safe). */
export function Brand() {
  return (
    <Link to="/" className="brand" aria-label="SwiftPay home">
      <span className="mark" aria-hidden="true">
        <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
          <path d="M3 12c5-1 8-7 14-8" stroke="#e8c36a" strokeWidth="2" strokeLinecap="round" />
          <circle cx="15" cy="5" r="1.6" fill="#e8c36a" />
        </svg>
      </span>
      <span>
        <strong>SwiftPay</strong>
        <span>Digital bank</span>
      </span>
    </Link>
  );
}
