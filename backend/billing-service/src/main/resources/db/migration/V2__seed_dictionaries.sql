INSERT INTO sub_tiers (id, name)      VALUES (1,'BASIC'),(2,'PREMIUM');
INSERT INTO sub_statuses (id, name)   VALUES (1,'ACTIVE'),(2,'CANCELED'),(3,'EXPIRED');
INSERT INTO txn_statuses (id, name)   VALUES (1,'PENDING'),(2,'SUCCESS'),(3,'FAILED'),(4,'REFUNDED');
INSERT INTO txn_types (id, name)      VALUES (1,'PURCHASE'),(2,'RENEWAL'),(3,'TRIAL'),(4,'REFUND');
INSERT INTO discount_types (id, name) VALUES (1,'PERCENT'),(2,'FIXED');

INSERT INTO plans (id, code, tier_id, duration_days, price_amount, currency, is_active, is_public) VALUES
  (1,'MONTH_1', 1, 30,  499.00, 'RUB', TRUE, TRUE),
  (2,'MONTH_3', 1, 90, 1299.00, 'RUB', TRUE, TRUE),
  (3,'TRIAL_30',1, 30,    0.00, 'RUB', TRUE, FALSE);
