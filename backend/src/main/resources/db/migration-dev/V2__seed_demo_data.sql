-- V2: demo data (DEV ONLY – put under a dev-only Flyway location or profile, not in production)
-- All demo users share the password: Test1234!   (BCrypt hash below)

INSERT INTO customer (id, username, password_hash, full_name) VALUES
 (1, 'ecem', '$2a$10$EgPPCrse0odTQDd//VQo1uPxZJMCe3YL0d.k8xwhUjRzde.1qhZ5i', 'Ecem Demo'),
 (2, 'ali',  '$2a$10$EgPPCrse0odTQDd//VQo1uPxZJMCe3YL0d.k8xwhUjRzde.1qhZ5i', 'Ali Demo');

INSERT INTO account (id, customer_id, iban, balance) VALUES
 (1, 1, 'TR620006100000000000001001', 250000.00),
 (2, 1, 'TR350006100000000000001002',  10000.00),
 (3, 2, 'TR690006200000000000002001', 100000.00),
 (4, 2, 'TR350006200000000000003001',   5000.00);

-- keep sequences in sync with explicit ids
SELECT setval(pg_get_serial_sequence('customer', 'id'), (SELECT MAX(id) FROM customer));
SELECT setval(pg_get_serial_sequence('account',  'id'), (SELECT MAX(id) FROM account));
