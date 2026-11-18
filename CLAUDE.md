# CLAUDE.md – SwiftPay Money Transfer

Instant money transfer between accounts inside the same system (digital bank).
**Priorities: correctness, consistency, security.** Read `API_SPEC.md` first and follow it exactly.

## Scope
In: create transfer, duplicate protection, transfer limits, list/detail transfers, monthly statistics, optional React UI.
Out: registration, KYC, cards, loans, external payment networks.

## Stack (decided – do not change without asking)
- Java 21, Spring Boot 3.x, Maven
- Spring Web, Spring Data JPA, Spring Security (JWT), Bean Validation
- PostgreSQL + Flyway (no H2; lock behaviour must be tested on a real DB)
- Testing: JUnit 5, Mockito, MockMvc, Testcontainers (PostgreSQL), JaCoCo (min 80%)
- springdoc-openapi, Spring Actuator (health only)
- Frontend (optional): Vite + React + TypeScript, React Router, TanStack Query, React Hook Form + Zod
- Docker Compose: `db`, `backend`, `frontend`

## Repo layout
```
/backend   /frontend   /postman   docker-compose.yml   README.md   API_SPEC.md   CLAUDE.md
```
Backend packages: `controller / service / repository / domain / dto / config / exception / security`

## Hard rules
1. **Money = `BigDecimal`**, DB `NUMERIC(19,2)`, never float/double. JSON money fields are **strings**.
2. **Transfer = one transaction**: debit, credit, transfer record and status update commit or roll back together.
3. **Concurrency**: lock both accounts with pessimistic write lock (`SELECT ... FOR UPDATE`) **always in ascending account id order** to prevent deadlocks. Limit and balance checks happen inside the lock.
4. **Idempotency**: `Idempotency-Key` header required on `POST /transfers`. Unique constraint on `(customer_id, idempotency_key)`. Store a request hash.
   - same key + same payload -> return the original result (200, `Idempotent-Replayed: true`)
   - same key + different payload -> 409 `IDEMPOTENCY_KEY_CONFLICT`
   - concurrent duplicate -> exactly one transfer is created
5. **Limits** (configurable via `application.yml`, global defaults): per transfer 50,000 / daily 100,000 / monthly 500,000 TRY. Only `COMPLETED` transfers count. Day/month boundaries use `Europe/Istanbul`.
6. **Time**: inject `java.time.Clock`; never call `Instant.now()` / `LocalDate.now()` directly in services.
7. **Authorization**: every query is scoped to the authenticated customer. Never trust ids from the client. Accessing someone else's transfer/account returns 404 (do not leak existence).
8. **Never expose entities**; use DTOs. Never leak stack traces or internal messages.
9. **Logging**: no passwords, tokens or full IBANs. Mask IBANs (`TR33 **** **** **** **** 1326`). Every log line carries the trace id (MDC).
10. **Responses**: success wrapper `{data, traceId, timestamp}`; error `{code, message, traceId, timestamp, fieldErrors?}`. One global `@RestControllerAdvice`. Error codes are an enum.
11. Status machine: `PENDING -> COMPLETED | FAILED`; no other transitions. Failed attempts are stored with a reason and do **not** count toward limits.
12. Secrets (JWT secret, DB password) come from env variables. Provide `.env.example`. Nothing secret in git.

## Security checklist
JWT auth (short expiry), BCrypt passwords, rate limit on `POST /transfers` and login, restricted CORS (explicit origin, no `*`), security headers, generic login error message, validation on all inputs, IDOR tests.

## Testing requirements
- Unit tests: limit boundaries (50,000 / 50,000.01, daily/monthly exact edge), insufficient balance, same-account transfer, invalid IBAN, decimals, status transitions.
- Integration (Testcontainers): full create-transfer flow, auth + ownership, statistics (6 months, zero-filled, only COMPLETED, newest first), date range filter.
- **Concurrency test**: N parallel transfers from one account; balance never negative, limits never exceeded.
- **Idempotency test**: parallel requests with same key -> single transfer.
- Time-boundary tests with a fixed `Clock` (23:59:59 vs 00:00:00, month rollover).
- JaCoCo report; fail the build under 80%.

## Working style with the agent
- Work in small steps: migrations -> domain -> service -> controller -> tests. Run `mvn test` after each step.
- Follow `API_SPEC.md` for paths, payloads and error codes; if something is ambiguous, state the assumption and add it to the README "Assumptions" section (bold).
- Keep code clean: small methods, meaningful names, no dead code.
- Record meaningful AI-assisted decisions in the README "AI usage" section.

## Assumptions (copy to README, highlighted)
- **Currency is TRY only.**
- **Auth is simple JWT with seeded demo customers.**
- **Failed business-rule attempts are stored as FAILED and do not count toward limits.**
- **Receiver IBAN must exist in the system; otherwise `RECEIVER_NOT_FOUND`.**
- **Transfers to the same account are forbidden; transfers between a customer's own accounts are allowed.**
- **Limits are global config values (no per-customer override).**
- **Day/month boundaries use Europe/Istanbul time.**
- **Unauthorized access to another customer's resource returns 404.**

## Definition of done
`docker compose up` starts everything; seeded users documented in README; `mvn test` green with >= 80% coverage; Postman collection included (login stores the token automatically); README covers design, stack, how to run, credentials, assumptions.
