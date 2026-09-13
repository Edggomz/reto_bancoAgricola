-- Semilla de referencia (Edgar) — equivalente a com.bancoagricola.ruta.store.DataStore.
-- Ejecutar tras oracle-schema.sql.

INSERT INTO customer (id, username, first_name, display_name, initials, avatar_color, voice, card_tier, archetype, first_time_at_risk)
VALUES ('cust-edgar', 'edgar.gomez', 'Edgar', 'Edgar Gómez', 'EG', '#7E4FBC', 'tu', 'platino', 'diligente', 1);

INSERT INTO account (id, customer_id, type, product_name, number_masked, number_full, balance_available, currency, is_primary_source)
VALUES ('acc-0110', 'cust-edgar', 'savings', 'Max Electrónico', '····0110', '3007040110', 1482.14, 'USD', 1);

INSERT INTO credit (id, customer_id, kind, name, number_masked, currency, apartable, credit_limit, available, used_pct, pay_contado)
VALUES ('cred-card-4821', 'cust-edgar', 'card', 'Tarjeta de crédito', '····4821', 'USD', 1, 1000, 660, 34, 340);

INSERT INTO credit (id, customer_id, kind, name, number_masked, currency, apartable, installment_amount, current_due_day, operation_number)
VALUES ('cred-personal-0452', 'cust-edgar', 'personal', 'Crédito personal', '····0452', 'USD', 1, 248.50, 28, '3242785');

INSERT INTO offer (id, okey, title, subtitle, highlighted)
VALUES ('offer-change-date', 'change-date', 'Cambiar fecha de cobro', 'Elige el día que te queda mejor', 1);
INSERT INTO offer (id, okey, title, subtitle, highlighted)
VALUES ('offer-term-deposit', 'term-deposit', 'Depósito a plazo digital', 'Haz crecer lo que ya tienes', 0);

INSERT INTO advisor (id, name, since_label, agency, initials, avatar_color)
VALUES ('adv-andrea', 'Andrea Portillo', 'marzo de 2024', 'Metrocentro', 'AP', '#00714E');

INSERT INTO advisory_topic (id, product_id, label) VALUES ('topic-personal', 'cred-personal-0452', 'Crédito personal');
INSERT INTO advisory_topic (id, product_id, label) VALUES ('topic-card', 'cred-card-4821', 'Tarjeta de crédito');
INSERT INTO advisory_topic (id, product_id, label) VALUES ('topic-savings', 'acc-0110', 'Cuenta Max Electrónico');
INSERT INTO advisory_topic (id, product_id, label) VALUES ('topic-other', 'other', 'Otro tema');

INSERT INTO notice (id, customer_id, kind, title, body, time_label, date_label, read_flag, actionable, target)
VALUES ('notice-shock', 'cust-edgar', 'shock', 'Este mes vino distinto, y está bien', 'La parte del 15 de noviembre no alcanzó. Podemos verlo juntos.', '8:05', 'HOY', 0, 1, 'advisory-shock');
INSERT INTO notice (id, customer_id, kind, title, date_label, read_flag, actionable)
VALUES ('notice-mitad', 'cust-edgar', 'progress', 'Ya va la mitad', '30 oct', 1, 0);
INSERT INTO notice (id, customer_id, kind, title, date_label, read_flag, actionable, target)
VALUES ('notice-pagado', 'cust-edgar', 'paid', 'Pagado, y a tiempo', '18 oct', 1, 1, 'record');
INSERT INTO notice (id, customer_id, kind, title, date_label, read_flag, actionable)
VALUES ('notice-completa', 'cust-edgar', 'complete', 'Tu cuota ya está completa', '15 oct', 1, 0);

INSERT INTO payment_record (customer_id, streak_months, next_plus_one_label, progress_current, progress_total, consults_note)
VALUES ('cust-edgar', 19, 'Tu próximo +1: 18 de noviembre', 22, 24, 'Consulta tu récord sin costo y sin límite.');

INSERT INTO record_milestone (customer_id, idx, label, date_label) VALUES ('cust-edgar', 1, '23 de 24', '23 feb 2027');
INSERT INTO record_milestone (customer_id, idx, label, date_label) VALUES ('cust-edgar', 2, '24 de 24', '24 mar 2027');

INSERT INTO record_sumando (id, customer_id, label) VALUES ('sum-1', 'cust-edgar', 'Pagaste octubre a tiempo');
INSERT INTO record_sumando (id, customer_id, label) VALUES ('sum-2', 'cust-edgar', 'Cambiaste tu fecha de cobro');
INSERT INTO record_sumando (id, customer_id, label) VALUES ('sum-3', 'cust-edgar', 'Apartas tu cuota en partes');

COMMIT;
