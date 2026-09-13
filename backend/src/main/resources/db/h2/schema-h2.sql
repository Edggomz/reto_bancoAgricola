-- ============================================================================
-- Ruta · Bancoagrícola — ESQUEMA H2 (perfil por defecto, MODE=Oracle)
-- ============================================================================
-- GENERADO por tools/seed/oracle-to-h2.mjs desde db/oracle-schema.sql. No editar
-- a mano: cambiar el esquema Oracle y volver a ejecutar el script.
-- Se omiten funciones PL/SQL, vistas e índices sobre expresiones: esa lógica
-- vive en Java y es la misma en Oracle y en H2.
-- ============================================================================

CREATE TABLE PARAMETRO_APP (
  clave        VARCHAR2(96)   NOT NULL,
  valor        VARCHAR2(2000) NOT NULL,
  descripcion  VARCHAR2(400),
  CONSTRAINT pk_parametro_app PRIMARY KEY (clave)
);

CREATE TABLE PARAMETRO_CORTE (
  dia_corte    NUMBER(2)     NOT NULL,
  descripcion  VARCHAR2(128),
  activo       NUMBER(1)     DEFAULT 1 NOT NULL,
  CONSTRAINT pk_parametro_corte PRIMARY KEY (dia_corte),
  CONSTRAINT ck_corte_rango     CHECK (dia_corte BETWEEN 1 AND 28),
  CONSTRAINT ck_corte_activo    CHECK (activo IN (0, 1))
);

CREATE TABLE CATALOGO_FRECUENCIA (
  id                VARCHAR2(64)  NOT NULL,
  slug              VARCHAR2(32)  NOT NULL,
  label             VARCHAR2(96)  NOT NULL,
  descripcion       VARCHAR2(256),
  tipo              VARCHAR2(16)  DEFAULT 'mensual' NOT NULL,
  offset_min        NUMBER(2)     DEFAULT 3 NOT NULL,
  offset_max        NUMBER(2)     DEFAULT 4 NOT NULL,
  partes_permitidas VARCHAR2(16)  DEFAULT '2,3,4' NOT NULL,
  partes_sugeridas  NUMBER(1)     DEFAULT 2 NOT NULL,
  nota_partes       VARCHAR2(200),
  orden             NUMBER(3)     DEFAULT 0 NOT NULL,
  activo            NUMBER(1)     DEFAULT 1 NOT NULL,
  CONSTRAINT pk_cat_frecuencia    PRIMARY KEY (id),
  CONSTRAINT uk_cat_frecuencia    UNIQUE (slug),
  CONSTRAINT ck_frecuencia_tipo   CHECK (tipo IN ('mensual', 'semanal', 'variable')),
  CONSTRAINT ck_frecuencia_partes CHECK (partes_sugeridas BETWEEN 1 AND 4),
  CONSTRAINT ck_frecuencia_offset CHECK (offset_min BETWEEN 0 AND 10 AND offset_min <= offset_max),
  CONSTRAINT ck_frecuencia_activo CHECK (activo IN (0, 1))
);

CREATE TABLE CATALOGO_FRECUENCIA_DIA (
  frecuencia_id VARCHAR2(64) NOT NULL,
  idx           NUMBER(2)    NOT NULL,
  dia_pago      NUMBER(2),
  fin_de_mes    NUMBER(1)    DEFAULT 0 NOT NULL,
  dia_semana    VARCHAR2(16),
  opcion_id     VARCHAR2(64),
  label         VARCHAR2(64),
  hint          VARCHAR2(64),
  titulo        VARCHAR2(64),
  pago_label    VARCHAR2(48),
  CONSTRAINT pk_cat_frec_dia   PRIMARY KEY (frecuencia_id, idx),
  CONSTRAINT fk_cat_frec_dia   FOREIGN KEY (frecuencia_id)
    REFERENCES CATALOGO_FRECUENCIA (id) ON DELETE CASCADE,
  CONSTRAINT ck_cat_frec_forma CHECK (dia_pago IS NOT NULL OR fin_de_mes = 1 OR dia_semana IS NOT NULL),
  CONSTRAINT ck_cat_frec_rango CHECK (dia_pago IS NULL OR dia_pago BETWEEN 1 AND 28),
  CONSTRAINT ck_cat_frec_fin   CHECK (fin_de_mes IN (0, 1)),
  CONSTRAINT ck_cat_frec_sem   CHECK (dia_semana IS NULL OR dia_semana IN
    ('lunes', 'martes', 'miercoles', 'jueves', 'viernes', 'sabado', 'domingo'))
);

CREATE TABLE CATALOGO_PARTES (
  parts   NUMBER(1)    NOT NULL,
  label   VARCHAR2(32) NOT NULL,
  orden   NUMBER(3)    DEFAULT 0 NOT NULL,
  activo  NUMBER(1)    DEFAULT 1 NOT NULL,
  CONSTRAINT pk_cat_partes     PRIMARY KEY (parts),
  CONSTRAINT ck_cat_partes_n   CHECK (parts IN (1, 2, 3, 4)),
  CONSTRAINT ck_cat_partes_act CHECK (activo IN (0, 1))
);

CREATE TABLE CATALOGO_DIA_ASESORIA (
  id          VARCHAR2(64) NOT NULL,
  label       VARCHAR2(64) NOT NULL,
  when_label  VARCHAR2(64) NOT NULL,
  orden       NUMBER(3)    DEFAULT 0 NOT NULL,
  activo      NUMBER(1)    DEFAULT 1 NOT NULL,
  CONSTRAINT pk_cat_dia_ases PRIMARY KEY (id),
  CONSTRAINT ck_cat_dia_act  CHECK (activo IN (0, 1))
);

CREATE TABLE CATALOGO_HORA_ASESORIA (
  id      VARCHAR2(64) NOT NULL,
  label   VARCHAR2(64) NOT NULL,
  orden   NUMBER(3)    DEFAULT 0 NOT NULL,
  activo  NUMBER(1)    DEFAULT 1 NOT NULL,
  CONSTRAINT pk_cat_hora_ases PRIMARY KEY (id),
  CONSTRAINT ck_cat_hora_act  CHECK (activo IN (0, 1))
);

CREATE TABLE CATALOGO_CHAT_OPCION (
  id        VARCHAR2(64)  NOT NULL,
  label     VARCHAR2(160) NOT NULL,
  accion    VARCHAR2(32)  NOT NULL,
  contexto  VARCHAR2(16)  NOT NULL,
  orden     NUMBER(3)     DEFAULT 0 NOT NULL,
  activo    NUMBER(1)     DEFAULT 1 NOT NULL,
  CONSTRAINT pk_cat_chat_opcion PRIMARY KEY (id),
  CONSTRAINT ck_cat_chat_accion CHECK (accion IN
    ('fecha', 'apartar', 'automatico', 'asesora', 'agendar', 'no_gracias', 'seguir')),
  CONSTRAINT ck_cat_chat_ctx    CHECK (contexto IN ('oferta', 'escalamiento')),
  CONSTRAINT ck_cat_chat_act    CHECK (activo IN (0, 1))
);

CREATE TABLE OFERTA (
  id           VARCHAR2(64)  NOT NULL,
  okey         VARCHAR2(32)  NOT NULL,
  title        VARCHAR2(128) NOT NULL,
  subtitle     VARCHAR2(256),
  highlighted  NUMBER(1)     DEFAULT 0 NOT NULL,
  orden        NUMBER(3)     DEFAULT 0 NOT NULL,
  activo       NUMBER(1)     DEFAULT 1 NOT NULL,
  CONSTRAINT pk_oferta      PRIMARY KEY (id),
  CONSTRAINT ck_oferta_key  CHECK (okey IN ('change-date', 'term-deposit')),
  CONSTRAINT ck_oferta_high CHECK (highlighted IN (0, 1)),
  CONSTRAINT ck_oferta_act  CHECK (activo IN (0, 1))
);

CREATE TABLE OFERTA_APERTURA (
  id                    VARCHAR2(64)  NOT NULL,
  product_name          VARCHAR2(128) NOT NULL,
  account_product_name  VARCHAR2(96)  NOT NULL,
  account_tipo          VARCHAR2(16)  DEFAULT 'debit' NOT NULL,
  opening_cost          NUMBER(14,2)  DEFAULT 0 NOT NULL,
  monthly_cost          NUMBER(14,2)  DEFAULT 0 NOT NULL,
  activo                NUMBER(1)     DEFAULT 1 NOT NULL,
  CONSTRAINT pk_oferta_apertura   PRIMARY KEY (id),
  CONSTRAINT ck_oferta_apert_tipo CHECK (account_tipo IN ('savings', 'debit')),
  CONSTRAINT ck_oferta_apert_act  CHECK (activo IN (0, 1))
);

CREATE TABLE OFERTA_APERTURA_CONDICION (
  oferta_id VARCHAR2(64)  NOT NULL,
  idx       NUMBER(2)     NOT NULL,
  texto     VARCHAR2(400) NOT NULL,
  CONSTRAINT pk_oferta_apert_cond PRIMARY KEY (oferta_id, idx),
  CONSTRAINT fk_oferta_apert_cond FOREIGN KEY (oferta_id)
    REFERENCES OFERTA_APERTURA (id) ON DELETE CASCADE
);

CREATE TABLE OFERTA_APERTURA_DOCUMENTO (
  id        VARCHAR2(64)  NOT NULL,
  oferta_id VARCHAR2(64)  NOT NULL,
  titulo    VARCHAR2(128) NOT NULL,
  url       VARCHAR2(512) NOT NULL,
  orden     NUMBER(3)     DEFAULT 0 NOT NULL,
  CONSTRAINT pk_oferta_apert_doc PRIMARY KEY (id),
  CONSTRAINT fk_oferta_apert_doc FOREIGN KEY (oferta_id)
    REFERENCES OFERTA_APERTURA (id) ON DELETE CASCADE
);

CREATE TABLE ASESOR (
  id            VARCHAR2(64)  NOT NULL,
  name          VARCHAR2(128) NOT NULL,
  since_label   VARCHAR2(64),
  agency        VARCHAR2(96)  NOT NULL,
  initials      VARCHAR2(8),
  avatar_color  VARCHAR2(16),
  activo        NUMBER(1)     DEFAULT 1 NOT NULL,
  CONSTRAINT pk_asesor     PRIMARY KEY (id),
  CONSTRAINT ck_asesor_act CHECK (activo IN (0, 1))
);

CREATE TABLE ASESORIA_TEMA (
  id             VARCHAR2(64) NOT NULL,
  producto_tipo  VARCHAR2(16) NOT NULL,
  label          VARCHAR2(96) NOT NULL,
  orden          NUMBER(3)    DEFAULT 0 NOT NULL,
  activo         NUMBER(1)    DEFAULT 1 NOT NULL,
  CONSTRAINT pk_asesoria_tema   PRIMARY KEY (id),
  CONSTRAINT uk_asesoria_tipo   UNIQUE (producto_tipo),
  CONSTRAINT ck_asesoria_tipo   CHECK (producto_tipo IN ('personal', 'card', 'cuenta', 'other')),
  CONSTRAINT ck_asesoria_act    CHECK (activo IN (0, 1))
);

CREATE TABLE CLIENTE (
  id                  VARCHAR2(64)  NOT NULL,
  username            VARCHAR2(64)  NOT NULL,
  password_hash       VARCHAR2(128) NOT NULL,
  first_name          VARCHAR2(64)  NOT NULL,
  display_name        VARCHAR2(128) NOT NULL,
  initials            VARCHAR2(8)   NOT NULL,
  avatar_color        VARCHAR2(16)  NOT NULL,
  voice               VARCHAR2(8)   DEFAULT 'tu' NOT NULL,
  card_tier           VARCHAR2(16)  NOT NULL,
  archetype           VARCHAR2(20)  NOT NULL,
  first_time_at_risk  NUMBER(1)     DEFAULT 1 NOT NULL,
  perfil_crediticio   VARCHAR2(16)  NOT NULL,
  categoria           VARCHAR2(4)   NOT NULL,
  asesor_id           VARCHAR2(64)  NOT NULL,
  frecuencia_pago     VARCHAR2(64),
  telefono            VARCHAR2(20),
  municipio           VARCHAR2(64),
  departamento        VARCHAR2(64),
  fecha_alta          DATE          DEFAULT TRUNC(SYSDATE) NOT NULL,
  activo              NUMBER(1)     DEFAULT 1 NOT NULL,
  CONSTRAINT pk_cliente        PRIMARY KEY (id),
  CONSTRAINT uk_cliente_user   UNIQUE (username),
  CONSTRAINT fk_cliente_asesor FOREIGN KEY (asesor_id) REFERENCES ASESOR (id),
  CONSTRAINT fk_cliente_frec   FOREIGN KEY (frecuencia_pago) REFERENCES CATALOGO_FRECUENCIA (id),
  CONSTRAINT ck_cliente_voice  CHECK (voice = 'tu'),
  CONSTRAINT ck_cliente_tier   CHECK (card_tier IN ('clasica', 'oro', 'platino', 'black')),
  CONSTRAINT ck_cliente_arq    CHECK (archetype IN ('diligente', 'olvidadizo', 'resistente', 'despreocupado')),
  CONSTRAINT ck_cliente_perfil CHECK (perfil_crediticio IN ('impecable', 'mejorable', 'fatal')),
  CONSTRAINT ck_cliente_cat    CHECK (categoria IN ('A1', 'A2', 'B', 'C', 'D', 'E')),
  CONSTRAINT ck_cliente_risk   CHECK (first_time_at_risk IN (0, 1)),
  CONSTRAINT ck_cliente_act    CHECK (activo IN (0, 1)),
  CONSTRAINT ck_cliente_tel    CHECK (telefono IS NULL OR REGEXP_LIKE(telefono, '^\+[0-9]{8,15}$')),
  CONSTRAINT ck_cliente_coher  CHECK (
       (perfil_crediticio = 'impecable' AND categoria IN ('A1', 'A2'))
    OR (perfil_crediticio = 'mejorable' AND categoria IN ('B', 'C'))
    OR (perfil_crediticio = 'fatal'     AND categoria IN ('D', 'E')))
);

COMMENT ON COLUMN CLIENTE.card_tier IS 'Solo decide el canal de salida al escalar (platino/black -> asesora nombrada).';

COMMENT ON COLUMN CLIENTE.archetype IS 'Solo decide tono y orden de ofertas. Nunca monto ni plazo.';

COMMENT ON COLUMN CLIENTE.password_hash IS 'SHA-256 hex de ruta:<username>:<password>.';

CREATE TABLE SESION_TOKEN (
  token       VARCHAR2(96) NOT NULL,
  cliente_id  VARCHAR2(64) NOT NULL,
  created_at  TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL,
  expires_at  TIMESTAMP    NOT NULL,
  CONSTRAINT pk_sesion_token PRIMARY KEY (token),
  CONSTRAINT fk_sesion_cli   FOREIGN KEY (cliente_id)
    REFERENCES CLIENTE (id) ON DELETE CASCADE
);

CREATE TABLE CUENTA (
  id                 VARCHAR2(64)  NOT NULL,
  cliente_id         VARCHAR2(64)  NOT NULL,
  tipo               VARCHAR2(16)  NOT NULL,
  product_name       VARCHAR2(96)  NOT NULL,
  number_masked      VARCHAR2(24)  NOT NULL,
  number_full        VARCHAR2(32),
  balance_available  NUMBER(14,2)  DEFAULT 0 NOT NULL,
  balance_apartado   NUMBER(14,2)  DEFAULT 0 NOT NULL,
  currency           VARCHAR2(8)   DEFAULT 'USD' NOT NULL,
  is_primary_source  NUMBER(1)     DEFAULT 0 NOT NULL,
  created_at         TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT pk_cuenta       PRIMARY KEY (id),
  CONSTRAINT uk_cuenta_num   UNIQUE (number_full),
  CONSTRAINT fk_cuenta_cli   FOREIGN KEY (cliente_id)
    REFERENCES CLIENTE (id) ON DELETE CASCADE,
  CONSTRAINT ck_cuenta_tipo  CHECK (tipo IN ('savings', 'debit')),
  CONSTRAINT ck_cuenta_prim  CHECK (is_primary_source IN (0, 1)),
  CONSTRAINT ck_cuenta_saldo CHECK (balance_available >= 0 AND balance_apartado >= 0)
);

CREATE TABLE CREDITO (
  id                 VARCHAR2(64)  NOT NULL,
  cliente_id         VARCHAR2(64)  NOT NULL,
  kind               VARCHAR2(16)  NOT NULL,
  name               VARCHAR2(96)  NOT NULL,
  number_masked      VARCHAR2(24)  NOT NULL,
  currency           VARCHAR2(8)   DEFAULT 'USD' NOT NULL,
  apartable          NUMBER(1)     DEFAULT 1 NOT NULL,
  credit_limit       NUMBER(14,2),
  available          NUMBER(14,2),
  used_pct           NUMBER(3),
  pay_contado        NUMBER(14,2),
  installment_amount NUMBER(14,2),
  current_due_day    NUMBER(2),
  operation_number   VARCHAR2(32),
  saldo_capital      NUMBER(14,2),
  tasa_anual         NUMBER(7,4),
  dia_corte          NUMBER(2)     NOT NULL,
  fecha_apertura     DATE          DEFAULT TRUNC(SYSDATE) NOT NULL,
  estado_pago        VARCHAR2(20)  DEFAULT 'al_dia' NOT NULL,
  CONSTRAINT pk_credito        PRIMARY KEY (id),
  CONSTRAINT fk_credito_cli    FOREIGN KEY (cliente_id)
    REFERENCES CLIENTE (id) ON DELETE CASCADE,
  CONSTRAINT fk_credito_corte  FOREIGN KEY (dia_corte)
    REFERENCES PARAMETRO_CORTE (dia_corte),
  CONSTRAINT ck_credito_kind   CHECK (kind IN ('card', 'personal', 'hipotecario', 'bancario')),
  CONSTRAINT ck_credito_apart  CHECK (apartable IN (0, 1)),
  CONSTRAINT ck_credito_estado CHECK (estado_pago IN ('al_dia', 'parte_pendiente')),
  CONSTRAINT ck_credito_dueday CHECK (current_due_day IS NULL OR current_due_day BETWEEN 1 AND 31),
  CONSTRAINT ck_credito_pct    CHECK (used_pct IS NULL OR used_pct BETWEEN 0 AND 100),
  CONSTRAINT ck_credito_tasa   CHECK (tasa_anual IS NULL OR tasa_anual BETWEEN 0 AND 1),
  CONSTRAINT ck_credito_disp   CHECK (available IS NULL OR available BETWEEN 0 AND credit_limit),
  CONSTRAINT ck_credito_forma  CHECK (
    (kind = 'card'
       AND credit_limit IS NOT NULL AND available IS NOT NULL
       AND used_pct IS NOT NULL AND pay_contado IS NOT NULL
       AND installment_amount IS NULL AND current_due_day IS NULL AND operation_number IS NULL)
    OR
    (kind IN ('personal', 'hipotecario', 'bancario')
       AND installment_amount IS NOT NULL AND current_due_day IS NOT NULL AND operation_number IS NOT NULL
       AND credit_limit IS NULL AND available IS NULL AND used_pct IS NULL AND pay_contado IS NULL))
);

COMMENT ON COLUMN CREDITO.dia_corte IS 'Día de corte del ciclo (1..28). Base de VW_SALDO_CICLO y del push preventivo.';

CREATE TABLE PRODUCTO_ACTIVADO (
  id          VARCHAR2(64) NOT NULL,
  cliente_id  VARCHAR2(64) NOT NULL,
  oferta_id   VARCHAR2(64) NOT NULL,
  detalle     VARCHAR2(200),
  created_at  TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT pk_producto_activado PRIMARY KEY (id),
  CONSTRAINT uk_producto_activado UNIQUE (cliente_id, oferta_id),
  CONSTRAINT fk_prod_act_cliente  FOREIGN KEY (cliente_id)
    REFERENCES CLIENTE (id) ON DELETE CASCADE,
  CONSTRAINT fk_prod_act_oferta   FOREIGN KEY (oferta_id)
    REFERENCES OFERTA (id) ON DELETE CASCADE
);

CREATE TABLE PLAN_FECHA_COBRO (
  credito_id            VARCHAR2(64) NOT NULL,
  new_day               NUMBER(2)    NOT NULL,
  effective_from_label  VARCHAR2(96) NOT NULL,
  effective_from        DATE,
  amount_unchanged      NUMBER(1)    DEFAULT 1 NOT NULL,
  term_unchanged        NUMBER(1)    DEFAULT 1 NOT NULL,
  frecuencia_id         VARCHAR2(64) NOT NULL,
  opcion_id             VARCHAR2(64) NOT NULL,
  dias_extra            NUMBER(3)     DEFAULT 0 NOT NULL,
  interes_extra         NUMBER(14,2)  DEFAULT 0 NOT NULL,
  acepto_interes        NUMBER(1)     DEFAULT 0 NOT NULL,
  canal                 VARCHAR2(8)   DEFAULT 'app' NOT NULL,
  bloqueado_hasta       DATE,
  created_at            TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT pk_plan_fecha      PRIMARY KEY (credito_id),
  CONSTRAINT fk_plan_fecha_cred FOREIGN KEY (credito_id)
    REFERENCES CREDITO (id) ON DELETE CASCADE,
  CONSTRAINT fk_plan_fecha_frec FOREIGN KEY (frecuencia_id)
    REFERENCES CATALOGO_FRECUENCIA (id),
  CONSTRAINT ck_plan_fecha_day  CHECK (new_day BETWEEN 0 AND 27),
  CONSTRAINT ck_plan_fecha_inv  CHECK (amount_unchanged = 1 AND term_unchanged = 1),
  CONSTRAINT ck_plan_fecha_int  CHECK (interes_extra = 0 OR acepto_interes = 1),
  CONSTRAINT ck_plan_fecha_acep CHECK (acepto_interes IN (0, 1)),
  CONSTRAINT ck_plan_fecha_can  CHECK (canal IN ('app', 'voz'))
);

CREATE TABLE APARTADO (
  id                            VARCHAR2(64) NOT NULL,
  credito_id                    VARCHAR2(64) NOT NULL,
  parts                         NUMBER(1)    NOT NULL,
  source_account_id             VARCHAR2(64) NOT NULL,
  pays_on_label                 VARCHAR2(96) NOT NULL,
  automatic                     NUMBER(1)    DEFAULT 0 NOT NULL,
  first_full_installment_label  VARCHAR2(96) NOT NULL,
  monto_total                   NUMBER(14,2) NOT NULL,
  fecha_pago                    DATE         NOT NULL,
  estado                        VARCHAR2(16) DEFAULT 'activo' NOT NULL,
  created_at                    TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT pk_apartado        PRIMARY KEY (id),
  CONSTRAINT fk_apartado_cred   FOREIGN KEY (credito_id)
    REFERENCES CREDITO (id) ON DELETE CASCADE,
  CONSTRAINT fk_apartado_cuenta FOREIGN KEY (source_account_id)
    REFERENCES CUENTA (id),
  CONSTRAINT fk_apartado_parts  FOREIGN KEY (parts)
    REFERENCES CATALOGO_PARTES (parts),
  CONSTRAINT ck_apartado_auto   CHECK (automatic IN (0, 1)),
  CONSTRAINT ck_apartado_monto  CHECK (monto_total > 0),
  CONSTRAINT ck_apartado_estado CHECK (estado IN ('activo', 'cancelado', 'cumplido'))
);

CREATE TABLE APARTADO_CUOTA (
  apartado_id VARCHAR2(64) NOT NULL,
  idx         NUMBER(2)    NOT NULL,
  label       VARCHAR2(64) NOT NULL,
  fecha       DATE         NOT NULL,
  amount      NUMBER(14,2) NOT NULL,
  estado      VARCHAR2(16) DEFAULT 'pendiente' NOT NULL,
  CONSTRAINT pk_apartado_cuota PRIMARY KEY (apartado_id, idx),
  CONSTRAINT ck_apartado_c_est CHECK (estado IN ('pendiente', 'apartada', 'no_alcanzo')),
  CONSTRAINT fk_apartado_cuota FOREIGN KEY (apartado_id)
    REFERENCES APARTADO (id) ON DELETE CASCADE,
  CONSTRAINT ck_apartado_c_idx CHECK (idx BETWEEN 1 AND 4),
  CONSTRAINT ck_apartado_c_amt CHECK (amount > 0)
);

CREATE TABLE AUTOPAGO (
  id          VARCHAR2(64) NOT NULL,
  credito_id  VARCHAR2(64) NOT NULL,
  account_id  VARCHAR2(64) NOT NULL,
  active      NUMBER(1)    DEFAULT 1 NOT NULL,
  created_at  TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT pk_autopago      PRIMARY KEY (id),
  CONSTRAINT fk_autopago_cred FOREIGN KEY (credito_id)
    REFERENCES CREDITO (id) ON DELETE CASCADE,
  CONSTRAINT fk_autopago_cta  FOREIGN KEY (account_id)
    REFERENCES CUENTA (id),
  CONSTRAINT ck_autopago_act  CHECK (active IN (0, 1))
);

CREATE TABLE AVISO (
  id          VARCHAR2(64)  NOT NULL,
  cliente_id  VARCHAR2(64)  NOT NULL,
  kind        VARCHAR2(16)  NOT NULL,
  title       VARCHAR2(160) NOT NULL,
  body        VARCHAR2(512),
  time_label  VARCHAR2(16),
  date_label  VARCHAR2(24)  NOT NULL,
  read_flag   NUMBER(1)     DEFAULT 0 NOT NULL,
  actionable  NUMBER(1)     DEFAULT 0 NOT NULL,
  target      VARCHAR2(32),
  orden       NUMBER(4)     DEFAULT 0 NOT NULL,
  created_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT pk_aviso        PRIMARY KEY (id),
  CONSTRAINT fk_aviso_cli    FOREIGN KEY (cliente_id)
    REFERENCES CLIENTE (id) ON DELETE CASCADE,
  CONSTRAINT ck_aviso_kind   CHECK (kind IN ('confirm', 'progress', 'paid', 'complete', 'shock', 'corte')),
  CONSTRAINT ck_aviso_target CHECK (target IS NULL OR target IN ('record', 'advisory-shock')),
  CONSTRAINT ck_aviso_read   CHECK (read_flag IN (0, 1)),
  CONSTRAINT ck_aviso_act    CHECK (actionable IN (0, 1)),
  CONSTRAINT ck_aviso_coher  CHECK ((actionable = 0 AND target IS NULL)
                                 OR (actionable = 1 AND target IS NOT NULL))
);

CREATE TABLE RECORD_PAGO (
  cliente_id           VARCHAR2(64)  NOT NULL,
  streak_months        NUMBER(4)     DEFAULT 0 NOT NULL,
  next_plus_one_label  VARCHAR2(96)  NOT NULL,
  progress_current     NUMBER(4)     DEFAULT 0 NOT NULL,
  progress_total       NUMBER(4)     DEFAULT 24 NOT NULL,
  consults_note        VARCHAR2(200) NOT NULL,
  CONSTRAINT pk_record_pago     PRIMARY KEY (cliente_id),
  CONSTRAINT fk_record_pago_cli FOREIGN KEY (cliente_id)
    REFERENCES CLIENTE (id) ON DELETE CASCADE,
  CONSTRAINT ck_record_progreso CHECK (progress_current BETWEEN 0 AND progress_total),
  CONSTRAINT ck_record_streak   CHECK (streak_months >= 0)
);

CREATE TABLE RECORD_HITO (
  cliente_id  VARCHAR2(64) NOT NULL,
  idx         NUMBER(2)    NOT NULL,
  label       VARCHAR2(48) NOT NULL,
  date_label  VARCHAR2(48) NOT NULL,
  CONSTRAINT pk_record_hito PRIMARY KEY (cliente_id, idx),
  CONSTRAINT fk_record_hito FOREIGN KEY (cliente_id)
    REFERENCES CLIENTE (id) ON DELETE CASCADE
);

CREATE TABLE RECORD_SUMANDO (
  id          VARCHAR2(64)  NOT NULL,
  cliente_id  VARCHAR2(64)  NOT NULL,
  label       VARCHAR2(200) NOT NULL,
  orden       NUMBER(3)     DEFAULT 0 NOT NULL,
  CONSTRAINT pk_record_sumando PRIMARY KEY (id),
  CONSTRAINT fk_record_sumando FOREIGN KEY (cliente_id)
    REFERENCES CLIENTE (id) ON DELETE CASCADE
);

CREATE TABLE SHOCK_CONTEXT (
  cliente_id       VARCHAR2(64)  NOT NULL,
  credito_id       VARCHAR2(64),
  event_label      VARCHAR2(200) NOT NULL,
  reassurance      VARCHAR2(200) NOT NULL,
  amount           NUMBER(14,2)  NOT NULL,
  currency         VARCHAR2(8)   DEFAULT 'USD' NOT NULL,
  next_date_label  VARCHAR2(96)  NOT NULL,
  created_at       TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT pk_shock_context   PRIMARY KEY (cliente_id),
  CONSTRAINT fk_shock_context   FOREIGN KEY (cliente_id)
    REFERENCES CLIENTE (id) ON DELETE CASCADE,
  CONSTRAINT fk_shock_credito   FOREIGN KEY (credito_id)
    REFERENCES CREDITO (id) ON DELETE SET NULL,
  CONSTRAINT ck_shock_amount    CHECK (amount > 0)
);

CREATE TABLE CITA (
  id                 VARCHAR2(64)  NOT NULL,
  cliente_id         VARCHAR2(64)  NOT NULL,
  asesor_id          VARCHAR2(64)  NOT NULL,
  tema_id            VARCHAR2(64)  NOT NULL,
  producto_id        VARCHAR2(64),
  dia_id             VARCHAR2(64)  NOT NULL,
  hora_id            VARCHAR2(64)  NOT NULL,
  with_label         VARCHAR2(128) NOT NULL,
  when_label         VARCHAR2(96)  NOT NULL,
  where_label        VARCHAR2(128) NOT NULL,
  about_label        VARCHAR2(96)  NOT NULL,
  confirmation_note  VARCHAR2(400) NOT NULL,
  status             VARCHAR2(16)  DEFAULT 'scheduled' NOT NULL,
  source             VARCHAR2(16)  DEFAULT 'chat' NOT NULL,
  created_at         TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT pk_cita        PRIMARY KEY (id),
  CONSTRAINT fk_cita_cli    FOREIGN KEY (cliente_id)
    REFERENCES CLIENTE (id) ON DELETE CASCADE,
  CONSTRAINT fk_cita_asesor FOREIGN KEY (asesor_id) REFERENCES ASESOR (id),
  CONSTRAINT fk_cita_tema   FOREIGN KEY (tema_id) REFERENCES ASESORIA_TEMA (id),
  CONSTRAINT fk_cita_dia    FOREIGN KEY (dia_id) REFERENCES CATALOGO_DIA_ASESORIA (id),
  CONSTRAINT fk_cita_hora   FOREIGN KEY (hora_id) REFERENCES CATALOGO_HORA_ASESORIA (id),
  CONSTRAINT ck_cita_status CHECK (status IN ('scheduled', 'cancelled')),
  CONSTRAINT ck_cita_source CHECK (source IN ('chat', 'shock'))
);

CREATE TABLE CHAT_SESION (
  id               VARCHAR2(64) NOT NULL,
  cliente_id       VARCHAR2(64) NOT NULL,
  aviso_id         VARCHAR2(64),
  resultado        VARCHAR2(20) DEFAULT 'en_curso' NOT NULL,
  paso             VARCHAR2(24),
  intencion        VARCHAR2(24),
  credito_id       VARCHAR2(64),
  partes_elegidas  NUMBER(1),
  oferta_aceptada  VARCHAR2(64),
  fecha_acordada   DATE,
  turnos           NUMBER(4)    DEFAULT 0 NOT NULL,
  rechazos         NUMBER(3)    DEFAULT 0 NOT NULL,
  created_at       TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL,
  updated_at       TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL,
  closed_at        TIMESTAMP,
  CONSTRAINT pk_chat_sesion     PRIMARY KEY (id),
  CONSTRAINT fk_chat_sesion_cli FOREIGN KEY (cliente_id)
    REFERENCES CLIENTE (id) ON DELETE CASCADE,
  CONSTRAINT ck_chat_resultado  CHECK (resultado IN
    ('en_curso', 'acuerdo', 'negativa', 'siguiente_paso', 'escalado')),
  CONSTRAINT ck_chat_acuerdo    CHECK (resultado <> 'acuerdo' OR fecha_acordada IS NOT NULL),
  CONSTRAINT ck_chat_contadores CHECK (turnos >= 0 AND rechazos >= 0)
);

CREATE TABLE CHAT_MENSAJE (
  id          VARCHAR2(64) NOT NULL,
  sesion_id   VARCHAR2(64) NOT NULL,
  rol         VARCHAR2(16) NOT NULL,
  texto       CLOB         NOT NULL,
  origen      VARCHAR2(16) NOT NULL,
  proveedor   VARCHAR2(16),
  orden       NUMBER(6)    NOT NULL,
  created_at  TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT pk_chat_mensaje     PRIMARY KEY (id),
  CONSTRAINT fk_chat_mensaje_ses FOREIGN KEY (sesion_id)
    REFERENCES CHAT_SESION (id) ON DELETE CASCADE,
  CONSTRAINT ck_chat_rol         CHECK (rol IN ('assistant', 'user')),
  CONSTRAINT ck_chat_origen      CHECK (origen IN ('usuario', 'modelo', 'guion')),
  CONSTRAINT ck_chat_rol_origen  CHECK ((rol = 'user' AND origen = 'usuario')
                                     OR (rol = 'assistant' AND origen IN ('modelo', 'guion')))
);

CREATE TABLE CHAT_QUICK_REPLY (
  mensaje_id VARCHAR2(64)  NOT NULL,
  idx        NUMBER(2)     NOT NULL,
  opcion_id  VARCHAR2(64)  NOT NULL,
  label      VARCHAR2(160) NOT NULL,
  next_step  VARCHAR2(64),
  CONSTRAINT pk_chat_quick PRIMARY KEY (mensaje_id, idx),
  CONSTRAINT fk_chat_quick FOREIGN KEY (mensaje_id)
    REFERENCES CHAT_MENSAJE (id) ON DELETE CASCADE
);

CREATE TABLE TRANSACCION (
  id                VARCHAR2(64)  NOT NULL,
  credito_id        VARCHAR2(64)  NOT NULL,
  numero_producto   VARCHAR2(24)  NOT NULL,
  fecha             DATE          DEFAULT SYSDATE NOT NULL,
  tipo              CHAR(1)       NOT NULL,
  monto             NUMBER(14,2)  NOT NULL,
  descripcion       VARCHAR2(200),
  frontera_contada  VARCHAR2(3),
  CONSTRAINT pk_transaccion      PRIMARY KEY (id),
  CONSTRAINT fk_transaccion_cred FOREIGN KEY (credito_id)
    REFERENCES CREDITO (id) ON DELETE CASCADE,
  CONSTRAINT ck_transaccion_tipo CHECK (tipo IN ('D', 'H')),
  CONSTRAINT ck_transaccion_mto  CHECK (monto > 0),
  CONSTRAINT ck_transaccion_fron CHECK (frontera_contada IS NULL
                                    OR frontera_contada IN ('A_B', 'B_C', 'C_D', 'D_E'))
);

CREATE TABLE DISPOSITIVO (
  id          VARCHAR2(64)  NOT NULL,
  cliente_id  VARCHAR2(64)  NOT NULL,
  push_token  VARCHAR2(512) NOT NULL,
  platform    VARCHAR2(16)  NOT NULL,
  activo      NUMBER(1)     DEFAULT 1 NOT NULL,
  created_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
  updated_at  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT pk_dispositivo      PRIMARY KEY (id),
  CONSTRAINT uk_dispositivo_tok  UNIQUE (push_token),
  CONSTRAINT fk_dispositivo_cli  FOREIGN KEY (cliente_id)
    REFERENCES CLIENTE (id) ON DELETE CASCADE,
  CONSTRAINT ck_dispositivo_plat CHECK (platform IN ('android', 'ios', 'web')),
  CONSTRAINT ck_dispositivo_act  CHECK (activo IN (0, 1))
);

CREATE TABLE NOTIFICACION_ENVIADA (
  id                VARCHAR2(64)  NOT NULL,
  cliente_id        VARCHAR2(64)  NOT NULL,
  credito_id        VARCHAR2(64),
  dispositivo_id    VARCHAR2(64),
  aviso_id          VARCHAR2(64),
  canal             VARCHAR2(16)  DEFAULT 'simulado' NOT NULL,
  tipo              VARCHAR2(32)  NOT NULL,
  titulo            VARCHAR2(160) NOT NULL,
  cuerpo            VARCHAR2(512) NOT NULL,
  fecha_envio       TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
  corte_fecha       DATE          NOT NULL,
  dias_antes_corte  NUMBER(3)     NOT NULL,
  estado            VARCHAR2(16)  NOT NULL,
  detalle_error     VARCHAR2(512),
  CONSTRAINT pk_notif_enviada PRIMARY KEY (id),
  CONSTRAINT fk_notif_cli     FOREIGN KEY (cliente_id)
    REFERENCES CLIENTE (id) ON DELETE CASCADE,
  CONSTRAINT fk_notif_cred    FOREIGN KEY (credito_id)
    REFERENCES CREDITO (id) ON DELETE CASCADE,
  CONSTRAINT fk_notif_disp    FOREIGN KEY (dispositivo_id)
    REFERENCES DISPOSITIVO (id) ON DELETE SET NULL,
  CONSTRAINT ck_notif_estado  CHECK (estado IN ('ENVIADO', 'SIMULADO', 'ERROR')),
  CONSTRAINT ck_notif_canal   CHECK (canal IN ('fcm', 'expo', 'simulado')),
  CONSTRAINT ck_notif_dias    CHECK (dias_antes_corte >= 0)
);

CREATE TABLE IA_LLAMADA (
  id           VARCHAR2(64)  NOT NULL,
  servicio     VARCHAR2(16)  NOT NULL,
  proveedor    VARCHAR2(16),
  modelo       VARCHAR2(96),
  exito        NUMBER(1)     NOT NULL,
  fallback     NUMBER(1)     DEFAULT 0 NOT NULL,
  latencia_ms  NUMBER(8)     DEFAULT 0 NOT NULL,
  error        VARCHAR2(512),
  guardrail    VARCHAR2(512),
  sesion_id    VARCHAR2(64),
  created_at   TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT pk_ia_llamada       PRIMARY KEY (id),
  CONSTRAINT ck_ia_servicio      CHECK (servicio IN ('chat', 'form', 'guard', 'sugerencia')),
  CONSTRAINT ck_ia_exito         CHECK (exito IN (0, 1)),
  CONSTRAINT ck_ia_fallback      CHECK (fallback IN (0, 1))
);

CREATE TABLE PERFIL_INGRESO (
  cliente_id         VARCHAR2(64)  NOT NULL,
  tipo_ingreso       VARCHAR2(16)  NOT NULL,
  ingreso_constante  NUMBER(1),
  dias_ingreso       VARCHAR2(32),
  canal_pago         VARCHAR2(16),
  usa_banca_linea    NUMBER(1),
  fuente             VARCHAR2(8)   DEFAULT 'voz' NOT NULL,
  updated_at         TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT pk_perfil_ingreso    PRIMARY KEY (cliente_id),
  CONSTRAINT fk_perfil_ing_cli    FOREIGN KEY (cliente_id) REFERENCES CLIENTE (id) ON DELETE CASCADE,
  CONSTRAINT ck_perfil_ing_tipo   CHECK (tipo_ingreso IN ('salario', 'pension', 'remesa', 'negocio', 'otro')),
  CONSTRAINT ck_perfil_ing_const  CHECK (ingreso_constante IS NULL OR ingreso_constante IN (0, 1)),
  CONSTRAINT ck_perfil_ing_canal  CHECK (canal_pago IS NULL OR canal_pago IN ('app', 'agencia', 'otro')),
  CONSTRAINT ck_perfil_ing_banca  CHECK (usa_banca_linea IS NULL OR usa_banca_linea IN (0, 1)),
  CONSTRAINT ck_perfil_ing_fuente CHECK (fuente IN ('voz', 'app'))
);

CREATE TABLE SUCURSAL (
  id            VARCHAR2(64)  NOT NULL,
  nombre        VARCHAR2(96)  NOT NULL,
  direccion     VARCHAR2(256) NOT NULL,
  municipio     VARCHAR2(64)  NOT NULL,
  departamento  VARCHAR2(64)  NOT NULL,
  horario       VARCHAR2(160) NOT NULL,
  activo        NUMBER(1)     DEFAULT 1 NOT NULL,
  CONSTRAINT pk_sucursal     PRIMARY KEY (id),
  CONSTRAINT ck_sucursal_act CHECK (activo IN (0, 1))
);

CREATE TABLE LLAMADA_VOZ (
  id              VARCHAR2(64)   NOT NULL,
  cliente_id      VARCHAR2(64)   NOT NULL,
  credito_id      VARCHAR2(64),
  telefono        VARCHAR2(20)   NOT NULL,
  proveedor       VARCHAR2(16)   DEFAULT 'vapi' NOT NULL,
  proveedor_id    VARCHAR2(64),
  intento         NUMBER(2)      DEFAULT 1 NOT NULL,
  estado          VARCHAR2(16)   DEFAULT 'programada' NOT NULL,
  resultado       VARCHAR2(20),
  motivo_fin      VARCHAR2(64),
  dia_nuevo       NUMBER(2),
  dias_extra      NUMBER(3),
  interes_extra   NUMBER(14,2),
  resumen         VARCHAR2(1000),
  transcripcion   CLOB,
  duracion_seg    NUMBER(6),
  created_at      TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
  ended_at        TIMESTAMP,
  CONSTRAINT pk_llamada_voz        PRIMARY KEY (id),
  CONSTRAINT fk_llamada_voz_cli    FOREIGN KEY (cliente_id) REFERENCES CLIENTE (id) ON DELETE CASCADE,
  CONSTRAINT ck_llamada_voz_estado CHECK (estado IN ('programada', 'terminada', 'fallida')),
  CONSTRAINT ck_llamada_voz_res    CHECK (resultado IS NULL OR resultado IN ('fecha_cambiada', 'sin_cambio', 'bloqueado',
    'no_contesto', 'buzon', 'tercero', 'volver_a_llamar', 'no_llamar')),
  CONSTRAINT ck_llamada_voz_dia    CHECK (resultado IS NULL OR resultado <> 'fecha_cambiada' OR dia_nuevo IS NOT NULL)
);

CREATE TABLE EVENTO_AUDITORIA (
  id           VARCHAR2(64)   NOT NULL,
  created_at   TIMESTAMP      NOT NULL,
  canal        VARCHAR2(16)   NOT NULL,
  tipo         VARCHAR2(48)   NOT NULL,
  nivel        VARCHAR2(8)    DEFAULT 'info' NOT NULL,
  cliente_id   VARCHAR2(64),
  referencia   VARCHAR2(64),
  resumen      VARCHAR2(400)  NOT NULL,
  detalle      CLOB,
  duracion_ms  NUMBER(8),
  CONSTRAINT pk_evento_auditoria PRIMARY KEY (id),
  CONSTRAINT ck_evento_canal     CHECK (canal IN ('app', 'chat', 'voz', 'n8n', 'sistema', 'admin')),
  CONSTRAINT ck_evento_nivel     CHECK (nivel IN ('info', 'aviso', 'error'))
);

CREATE INDEX ix_cliente_asesor      ON CLIENTE (asesor_id);

CREATE INDEX ix_cliente_perfil      ON CLIENTE (perfil_crediticio, categoria);

CREATE INDEX ix_sesion_tok_cli      ON SESION_TOKEN (cliente_id);

CREATE INDEX ix_cuenta_cliente      ON CUENTA (cliente_id);

CREATE INDEX ix_credito_cliente     ON CREDITO (cliente_id);

CREATE INDEX ix_credito_corte       ON CREDITO (dia_corte);

CREATE INDEX ix_prod_act_cliente    ON PRODUCTO_ACTIVADO (cliente_id);

CREATE INDEX ix_apartado_c_fecha    ON APARTADO_CUOTA (fecha, estado);

CREATE INDEX ix_apartado_credito    ON APARTADO (credito_id, estado);

CREATE INDEX ix_autopago_credito    ON AUTOPAGO (credito_id, active);

CREATE INDEX ix_aviso_cliente       ON AVISO (cliente_id, orden);

CREATE INDEX ix_hito_cliente        ON RECORD_HITO (cliente_id);

CREATE INDEX ix_sumando_cliente     ON RECORD_SUMANDO (cliente_id, orden);

CREATE INDEX ix_cita_cliente        ON CITA (cliente_id, status);

CREATE INDEX ix_chat_ses_cliente    ON CHAT_SESION (cliente_id, resultado);

CREATE INDEX ix_chat_msg_sesion     ON CHAT_MENSAJE (sesion_id, orden);

CREATE INDEX ix_transaccion_cred    ON TRANSACCION (credito_id, fecha);

CREATE INDEX ix_disp_cliente        ON DISPOSITIVO (cliente_id, activo);

CREATE INDEX ix_notif_cli_fecha     ON NOTIFICACION_ENVIADA (cliente_id, fecha_envio);

CREATE INDEX ix_ia_llamada_fecha    ON IA_LLAMADA (servicio, created_at);

CREATE INDEX ix_cliente_telefono    ON CLIENTE (telefono);

CREATE INDEX ix_llamada_voz_cli     ON LLAMADA_VOZ (cliente_id, created_at);

CREATE INDEX ix_evento_fecha        ON EVENTO_AUDITORIA (created_at);
