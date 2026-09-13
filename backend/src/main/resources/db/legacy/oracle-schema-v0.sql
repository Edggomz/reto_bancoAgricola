-- Ruta · Bancoagrícola — Esquema Oracle (ruta de migración desde el store en memoria)
-- Ejecutar en el esquema destino. Ajustar tipos/tamaños según estándares del banco.
-- Nota: las OPCIONES de formulario (frecuencias, fechas, partes, días, horas) se
-- calculan en el servicio (OptionCatalog) y no requieren tabla; pueden pasarse a
-- catálogo si se prefiere.

CREATE TABLE customer (
  id              VARCHAR2(64)  PRIMARY KEY,
  username        VARCHAR2(64)  NOT NULL UNIQUE,
  first_name      VARCHAR2(64)  NOT NULL,
  display_name    VARCHAR2(128) NOT NULL,
  initials        VARCHAR2(8),
  avatar_color    VARCHAR2(16),
  voice           VARCHAR2(8)   DEFAULT 'tu',
  card_tier       VARCHAR2(16)  NOT NULL,  -- clasica | oro | platino | black
  archetype       VARCHAR2(16)  NOT NULL,  -- diligente | olvidadizo | resistente | despreocupado
  first_time_at_risk NUMBER(1)  DEFAULT 1
);

CREATE TABLE account (
  id                 VARCHAR2(64) PRIMARY KEY,
  customer_id        VARCHAR2(64) NOT NULL REFERENCES customer(id),
  type               VARCHAR2(16) NOT NULL, -- savings | debit
  product_name       VARCHAR2(64) NOT NULL,
  number_masked      VARCHAR2(16) NOT NULL,
  number_full        VARCHAR2(32),
  balance_available  NUMBER(14,2) NOT NULL,
  currency           VARCHAR2(8)  DEFAULT 'USD',
  is_primary_source  NUMBER(1)    DEFAULT 0
);

CREATE TABLE credit (
  id                 VARCHAR2(64) PRIMARY KEY,
  customer_id        VARCHAR2(64) NOT NULL REFERENCES customer(id),
  kind               VARCHAR2(16) NOT NULL, -- card | personal
  name               VARCHAR2(64) NOT NULL,
  number_masked      VARCHAR2(16) NOT NULL,
  currency           VARCHAR2(8)  DEFAULT 'USD',
  apartable          NUMBER(1)    DEFAULT 1,
  -- card
  credit_limit       NUMBER(14,2),
  available          NUMBER(14,2),
  used_pct           NUMBER(3),
  pay_contado        NUMBER(14,2),
  -- personal
  installment_amount NUMBER(14,2),
  current_due_day    NUMBER(2),
  operation_number   VARCHAR2(32)
);

CREATE TABLE offer (
  id          VARCHAR2(64) PRIMARY KEY,
  okey        VARCHAR2(32) NOT NULL, -- change-date | term-deposit
  title       VARCHAR2(128) NOT NULL,
  subtitle    VARCHAR2(256),
  highlighted NUMBER(1) DEFAULT 0
);

CREATE TABLE payment_date_plan (
  credit_id            VARCHAR2(64) PRIMARY KEY REFERENCES credit(id),
  new_day              NUMBER(2)    NOT NULL,
  effective_from_label VARCHAR2(64) NOT NULL,
  amount_unchanged     NUMBER(1)    DEFAULT 1,
  term_unchanged       NUMBER(1)    DEFAULT 1
);

CREATE TABLE apartado (
  id                          VARCHAR2(64) PRIMARY KEY,
  credit_id                   VARCHAR2(64) NOT NULL REFERENCES credit(id),
  parts                       NUMBER(1)    NOT NULL, -- 2 | 3 | 4
  source_account_id           VARCHAR2(64) NOT NULL REFERENCES account(id),
  pays_on_label               VARCHAR2(64),
  automatic                   NUMBER(1)    DEFAULT 0,
  first_full_installment_label VARCHAR2(64)
);

CREATE TABLE apartado_installment (
  apartado_id VARCHAR2(64) NOT NULL REFERENCES apartado(id),
  idx         NUMBER(2)    NOT NULL,
  label       VARCHAR2(64) NOT NULL,
  date_iso    VARCHAR2(10) NOT NULL,
  amount      NUMBER(14,2) NOT NULL,
  PRIMARY KEY (apartado_id, idx)
);

CREATE TABLE autopay_config (
  id         VARCHAR2(64) PRIMARY KEY,
  credit_id  VARCHAR2(64) NOT NULL REFERENCES credit(id),
  account_id VARCHAR2(64) NOT NULL REFERENCES account(id),
  active     NUMBER(1)    DEFAULT 1
);

CREATE TABLE notice (
  id         VARCHAR2(64) PRIMARY KEY,
  customer_id VARCHAR2(64) NOT NULL REFERENCES customer(id),
  kind       VARCHAR2(16) NOT NULL, -- confirm | progress | paid | complete | shock
  title      VARCHAR2(128) NOT NULL,
  body       VARCHAR2(512),
  time_label VARCHAR2(16),
  date_label VARCHAR2(16) NOT NULL,
  read_flag  NUMBER(1) DEFAULT 0,
  actionable NUMBER(1) DEFAULT 0,
  target     VARCHAR2(32) -- record | advisory-shock
);

CREATE TABLE payment_record (
  customer_id       VARCHAR2(64) PRIMARY KEY REFERENCES customer(id),
  streak_months     NUMBER(4)    NOT NULL,
  next_plus_one_label VARCHAR2(64),
  progress_current  NUMBER(4),
  progress_total    NUMBER(4),
  consults_note     VARCHAR2(128)
);

CREATE TABLE record_milestone (
  customer_id VARCHAR2(64) NOT NULL REFERENCES customer(id),
  idx         NUMBER(2)    NOT NULL,
  label       VARCHAR2(32) NOT NULL,
  date_label  VARCHAR2(32) NOT NULL,
  PRIMARY KEY (customer_id, idx)
);

CREATE TABLE record_sumando (
  id          VARCHAR2(64) PRIMARY KEY,
  customer_id VARCHAR2(64) NOT NULL REFERENCES customer(id),
  label       VARCHAR2(128) NOT NULL
);

CREATE TABLE advisor (
  id           VARCHAR2(64) PRIMARY KEY,
  name         VARCHAR2(128) NOT NULL,
  since_label  VARCHAR2(32),
  agency       VARCHAR2(64),
  initials     VARCHAR2(8),
  avatar_color VARCHAR2(16)
);

CREATE TABLE advisory_topic (
  id         VARCHAR2(64) PRIMARY KEY,
  product_id VARCHAR2(64),
  label      VARCHAR2(64) NOT NULL
);

CREATE TABLE appointment (
  id               VARCHAR2(64) PRIMARY KEY,
  customer_id      VARCHAR2(64) REFERENCES customer(id),
  advisor_id       VARCHAR2(64) REFERENCES advisor(id),
  with_label       VARCHAR2(128),
  when_label       VARCHAR2(64),
  where_label      VARCHAR2(64),
  about_label      VARCHAR2(64),
  confirmation_note VARCHAR2(256),
  status           VARCHAR2(16) DEFAULT 'scheduled' -- scheduled | cancelled
);

CREATE TABLE chat_session (
  id          VARCHAR2(64) PRIMARY KEY,
  customer_id VARCHAR2(64) REFERENCES customer(id),
  created_at  TIMESTAMP DEFAULT SYSTIMESTAMP
);

CREATE TABLE chat_message (
  id         VARCHAR2(64) PRIMARY KEY,
  session_id VARCHAR2(64) NOT NULL REFERENCES chat_session(id),
  role       VARCHAR2(16) NOT NULL, -- assistant | user
  text       CLOB,
  created_at TIMESTAMP DEFAULT SYSTIMESTAMP
);
