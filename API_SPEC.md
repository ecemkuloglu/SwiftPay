# API_SPEC.md – SwiftPay

Base path `/api`. JSON. All endpoints except login require `Authorization: Bearer <token>`.
Money fields are **strings** (`"1500.00"`). Timestamps are ISO-8601 UTC. Date filters are `yyyy-MM-dd`, interpreted in `Europe/Istanbul`.
Every response carries an `X-Trace-Id` header and a `traceId` in the body.

## Common models

Success
```json
{ "data": {}, "traceId": "7f3c1a9e4b2d", "timestamp": "2026-05-14T09:30:00Z" }
```
Error
```json
{
  "code": "LIMIT_EXCEEDED",
  "message": "Daily transfer limit exceeded.",
  "traceId": "7f3c1a9e4b2d",
  "timestamp": "2026-05-14T09:30:00Z",
  "fieldErrors": [{ "field": "amount", "message": "must be greater than 0" }]
}
```
`fieldErrors` only for `VALIDATION_ERROR`.

Page (inside `data`)
```json
{ "content": [], "page": 0, "size": 10, "totalElements": 42, "totalPages": 5 }
```

## Auth

### POST /api/auth/login
```json
// request
{ "username": "ecem", "password": "Test1234!" }
// 200 data
{ "accessToken": "eyJhbGciOi...", "tokenType": "Bearer", "expiresIn": 900 }
```
Errors: 401 `INVALID_CREDENTIALS` (same message for unknown user / wrong password), 429 `TOO_MANY_ATTEMPTS`.

## Accounts

### GET /api/accounts
```json
// 200 data
[{ "id": 1, "iban": "TR330006100519786457841326", "maskedIban": "TR33 **** **** **** **** 1326", "currency": "TRY", "balance": "25000.00" }]
```

### GET /api/accounts/limits
```json
// 200 data
{
  "currency": "TRY",
  "perTransferLimit": "50000.00",
  "daily":   { "limit": "100000.00", "used": "35000.00",  "remaining": "65000.00" },
  "monthly": { "limit": "500000.00", "used": "120000.00", "remaining": "380000.00" }
}
```
`used` counts only COMPLETED transfers.

## Transfers

### POST /api/transfers
Header: `Idempotency-Key: <uuid>` (required -> else 400 `IDEMPOTENCY_KEY_REQUIRED`)
```json
// request
{ "sourceAccountId": 1, "receiverIban": "TR640006200000000000000002", "amount": "1500.00", "description": "Kira" }
// 201 data
{
  "id": 101, "sourceAccountId": 1,
  "receiverIban": "TR640006200000000000000002",
  "maskedReceiverIban": "TR64 **** **** **** **** 0002",
  "amount": "1500.00", "currency": "TRY", "description": "Kira",
  "status": "COMPLETED",
  "createdAt": "2026-05-14T09:30:00Z", "completedAt": "2026-05-14T09:30:00Z"
}
```
Replay of the same key + payload -> 200 with the original body and header `Idempotent-Replayed: true`.
Validation: amount > 0 and max 2 decimals; description max 140 chars; IBAN `TR` + 24 digits with valid checksum; source account must belong to caller; source != receiver.

### GET /api/transfers/{id}
Transfer object plus `sourceAccountMaskedIban` and, if FAILED, `failureReason`. Not found or not owned -> 404 `TRANSFER_NOT_FOUND`.

### GET /api/transfers
Query (optional): `page=0`, `size=10` (max 100), `startDate`, `endDate` (both inclusive), `sort=createdAt,desc`.
```json
// 200 data
{ "content": [{ "id": 101, "maskedReceiverIban": "TR64 **** 0002", "amount": "1500.00", "status": "COMPLETED", "createdAt": "2026-05-14T09:30:00Z" }],
  "page": 0, "size": 10, "totalElements": 1, "totalPages": 1 }
```
Errors: 400 `INVALID_DATE_RANGE`, 400 `VALIDATION_ERROR`.

## Statistics

### GET /api/statistics/monthly
Last 6 months including current, newest first, only COMPLETED, empty months returned as zero.
```json
// 200 data
[
  { "month": "2026-05", "totalTransferCount": 4, "totalTransferredAmount": "12450.00" },
  { "month": "2026-04", "totalTransferCount": 2, "totalTransferredAmount": "1800.00" },
  { "month": "2026-03", "totalTransferCount": 0, "totalTransferredAmount": "0.00" }
]
```

## Error codes

| HTTP | Code | When |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Field validation failed |
| 400 | `IDEMPOTENCY_KEY_REQUIRED` | Header missing |
| 400 | `INVALID_DATE_RANGE` | startDate > endDate |
| 401 | `UNAUTHORIZED` / `INVALID_CREDENTIALS` | Missing/expired token, bad login |
| 403 | `ACCOUNT_NOT_OWNED` | Source account not owned by caller |
| 404 | `TRANSFER_NOT_FOUND` / `RECEIVER_NOT_FOUND` | Not found or not accessible |
| 404 | `NOT_FOUND` | Unknown route (authenticated request) |
| 405 | `METHOD_NOT_ALLOWED` | HTTP method not supported by the route (`Allow` header lists the supported ones) |
| 409 | `IDEMPOTENCY_KEY_CONFLICT` / `REQUEST_IN_PROGRESS` | Key reused with different payload / still processing |
| 422 | `INSUFFICIENT_BALANCE` | Balance too low |
| 422 | `PER_TRANSFER_LIMIT_EXCEEDED` | Per-transfer limit exceeded |
| 422 | `DAILY_LIMIT_EXCEEDED` | Daily limit exceeded |
| 422 | `MONTHLY_LIMIT_EXCEEDED` | Monthly limit exceeded |
| 422 | `SAME_ACCOUNT_TRANSFER` | Source and receiver are the same |
| 429 | `TOO_MANY_REQUESTS` | Rate limit |
| 500 | `INTERNAL_ERROR` | Unexpected error (no details leaked) |
