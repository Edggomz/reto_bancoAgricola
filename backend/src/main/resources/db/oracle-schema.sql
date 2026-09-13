-- ============================================================================
-- Ruta · Bancoagrícola — ESQUEMA ORACLE (definitivo)
-- ============================================================================
-- Modelo alineado 1:1 al CONTRATO de la API, cuya fuente de verdad es
--   movil_BancoAgricola/src/api/types.ts      (DTOs)
--   movil_BancoAgricola/src/api/endpoints.ts  (rutas)
-- y su espejo en Java: com.bancoagricola.ruta.dto.Api.
--
-- El backend NO contiene datos de negocio en el código: datos, catálogos, copy
-- del producto y umbrales salen de estas tablas.
--
-- Del esquema anterior del equipo se reutilizan: el ledger TRANSACCION (con su
-- FRONTERA_CONTADA), FN_CORTE_SIGUIENTE / FN_CORTE_ANTERIOR / FN_ANTIGUEDAD_MESES
-- (misma lógica, adaptadas a CREDITO), VW_SALDO_CICLO, VW_CONTEO_REINCIDENCIA y
-- la categoría crediticia A1..E. Sus tablas TIPO_PRODUCTO y CUENTA_CLIENTE se
-- eliminan: este modelo las sustituye (CREDITO / CUENTA).
--
-- IDEMPOTENTE: puede ejecutarse varias veces (hace DROP previo, incluido el
-- esquema anterior). Ejecutar conectado como el dueño del esquema de la app:
--
--   sqlplus JULIOPALACIOS/<password>@//localhost:1521/XEPDB1 @oracle-schema.sql
--   SQL> @oracle-seed.sql
--
-- Convenciones:
--   * Ids VARCHAR2: el contrato define todo id como string.
--   * Booleanos NUMBER(1) con CHECK (0,1). Montos NUMBER(14,2).
--   * Las fechas de negocio viajan como etiqueta (`*_label`) porque el copy lo
--     define el banco; donde el sistema calcula, hay DATE.
-- ============================================================================

SET DEFINE OFF
SET SERVEROUTPUT ON

-- ----------------------------------------------------------------------------
-- 0. LIMPIEZA IDEMPOTENTE
-- ----------------------------------------------------------------------------
DECLARE
  TYPE t_names IS TABLE OF VARCHAR2(128);

  v_views t_names := t_names(
    'VW_DASHBOARD_PERFIL', 'VW_PUSH_CANDIDATOS', 'VW_CONTEO_REINCIDENCIA', 'VW_SALDO_CICLO'
  );

  v_funcs t_names := t_names(
    'FN_ANTIGUEDAD_MESES', 'FN_CORTE_ANTERIOR', 'FN_CORTE_SIGUIENTE'
  );

  v_tables t_names := t_names(
    'IA_LLAMADA', 'NOTIFICACION_ENVIADA', 'DISPOSITIVO', 'TRANSACCION',
    'CHAT_QUICK_REPLY', 'CHAT_MENSAJE', 'CHAT_SESION', 'CITA', 'SHOCK_CONTEXT',
    'RECORD_SUMANDO', 'RECORD_HITO', 'RECORD_PAGO', 'AVISO',
    'AUTOPAGO', 'APARTADO_CUOTA', 'APARTADO', 'PLAN_FECHA_COBRO',
    'PRODUCTO_ACTIVADO', 'CREDITO', 'CUENTA', 'SESION_TOKEN', 'CLIENTE', 'ASESORIA_TEMA', 'ASESOR',
    'OFERTA_APERTURA_DOCUMENTO', 'OFERTA_APERTURA_CONDICION', 'OFERTA_APERTURA', 'OFERTA',
    'CATALOGO_CHAT_OPCION', 'CATALOGO_HORA_ASESORIA', 'CATALOGO_DIA_ASESORIA',
    'CATALOGO_PARTES', 'CATALOGO_FRECUENCIA_DIA', 'CATALOGO_FRECUENCIA',
    'PARAMETRO_CORTE', 'PARAMETRO_APP',
    -- Esquema anterior del equipo (sustituido por este modelo).
    'CUENTA_CLIENTE', 'TIPO_PRODUCTO'
  );
BEGIN
  FOR i IN 1 .. v_views.COUNT LOOP
    BEGIN
      EXECUTE IMMEDIATE 'DROP VIEW ' || v_views(i);
    EXCEPTION WHEN OTHERS THEN
      IF SQLCODE NOT IN (-942, -4043) THEN RAISE; END IF;
    END;
  END LOOP;

  FOR i IN 1 .. v_funcs.COUNT LOOP
    BEGIN
      EXECUTE IMMEDIATE 'DROP FUNCTION ' || v_funcs(i);
    EXCEPTION WHEN OTHERS THEN
      IF SQLCODE NOT IN (-942, -4043) THEN RAISE; END IF;
    END;
  END LOOP;

  FOR i IN 1 .. v_tables.COUNT LOOP
    BEGIN
      EXECUTE IMMEDIATE 'DROP TABLE ' || v_tables(i) || ' CASCADE CONSTRAINTS PURGE';
    EXCEPTION WHEN OTHERS THEN
      IF SQLCODE != -942 THEN RAISE; END IF;
    END;
  END LOOP;

  DBMS_OUTPUT.PUT_LINE('Limpieza previa completada.');
END;
/

-- ============================================================================
-- 1. PARÁMETROS Y CATÁLOGOS
-- ============================================================================

-- Copy del producto y umbrales (clave/valor). Sustituye las constantes que
-- vivían en OptionCatalog, BankingService, AdvisoryService y AdvisorAiService.
CREATE TABLE PARAMETRO_APP (
  clave        VARCHAR2(96)   NOT NULL,
  valor        VARCHAR2(2000) NOT NULL,
  descripcion  VARCHAR2(400),
  CONSTRAINT pk_parametro_app PRIMARY KEY (clave)
);

-- Días de corte válidos (1..28: nunca 29-31, para no romper en febrero).
CREATE TABLE PARAMETRO_CORTE (
  dia_corte    NUMBER(2)     NOT NULL,
  descripcion  VARCHAR2(128),
  activo       NUMBER(1)     DEFAULT 1 NOT NULL,
  CONSTRAINT pk_parametro_corte PRIMARY KEY (dia_corte),
  CONSTRAINT ck_corte_rango     CHECK (dia_corte BETWEEN 1 AND 28),
  CONSTRAINT ck_corte_activo    CHECK (activo IN (0, 1))
);

-- ¿Qué día te pagan? (03) -> DTO PayFrequencyOption.
-- offset_min/offset_max codifican la regla «solo días en que ya le pagaron:
-- su pago +3 y +4». El servicio calcula los días; la regla vive aquí.
CREATE TABLE CATALOGO_FRECUENCIA (
  id                VARCHAR2(64)  NOT NULL,
  slug              VARCHAR2(32)  NOT NULL,
  label             VARCHAR2(96)  NOT NULL,
  descripcion       VARCHAR2(256),
  tipo              VARCHAR2(16)  DEFAULT 'mensual' NOT NULL,
  offset_min        NUMBER(2)     DEFAULT 3 NOT NULL,
  offset_max        NUMBER(2)     DEFAULT 4 NOT NULL,
  -- ¿En cuántas partes? depende de cuántas veces le pagan: quien cobra una vez
  -- al mes aparta en 1 parte; quien cobra por semana puede repartir en 4.
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

-- Días de pago de referencia de cada frecuencia.
--   mensual: dia_pago (15) o fin_de_mes = 1 (último día del mes).
--   semanal: dia_semana + label («Los lunes») + opcion_id («date-lun»).
CREATE TABLE CATALOGO_FRECUENCIA_DIA (
  frecuencia_id VARCHAR2(64) NOT NULL,
  idx           NUMBER(2)    NOT NULL,
  dia_pago      NUMBER(2),
  fin_de_mes    NUMBER(1)    DEFAULT 0 NOT NULL,
  dia_semana    VARCHAR2(16),
  opcion_id     VARCHAR2(64),
  label         VARCHAR2(64),
  hint          VARCHAR2(64),
  -- Título del grupo en «¿Qué día te queda mejor?» y cómo se llama ese pago.
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

-- ¿En cuántas partes? (07) -> DTO PartsOption.parts.
CREATE TABLE CATALOGO_PARTES (
  parts   NUMBER(1)    NOT NULL,
  label   VARCHAR2(32) NOT NULL,
  orden   NUMBER(3)    DEFAULT 0 NOT NULL,
  activo  NUMBER(1)    DEFAULT 1 NOT NULL,
  CONSTRAINT pk_cat_partes     PRIMARY KEY (parts),
  CONSTRAINT ck_cat_partes_n   CHECK (parts IN (1, 2, 3, 4)),
  CONSTRAINT ck_cat_partes_act CHECK (activo IN (0, 1))
);

-- Días y horas para la asistencia -> DTOs DayOption / TimeOption.
-- when_label evita «Este mañana»: cada día trae su forma para la confirmación.
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

-- Respuestas rápidas del chat del asesor -> DTO ChatQuickReply.
-- La persona decide con opciones; el orden por arquetipo lo decide el servicio.
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

-- Ofertas de Inicio (02) -> DTO Offer.
-- REGLA DE DISEÑO: highlighted = 1 (amarillo #FDDA24) es ACCIÓN, no decoración.
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

-- Un solo amarillo a la vez.
CREATE UNIQUE INDEX ux_oferta_destacada
  ON OFERTA (CASE WHEN highlighted = 1 AND activo = 1 THEN 1 END);

-- Apertura de cuenta (08a/08b) -> DTO AccountOpeningOffer.
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

-- ============================================================================
-- 2. NÚCLEO: ASESOR, TEMAS, CLIENTE, CUENTAS Y CRÉDITOS
-- ============================================================================

-- Asesora bancaria (ficticia) -> DTO Advisor.
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

-- Temas de asesoría por tipo de producto -> DTO AdvisoryTopic.
-- El servicio arma la lista de cada cliente cruzando este catálogo con sus
-- productos (productId = id real del crédito o cuenta). Los ids son estables
-- ('topic-personal', 'topic-other') porque el frontend los usa.
-- Para 'cuenta', label es plantilla: {producto} = nombre de la cuenta.
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

-- Cliente -> DTO Customer.
--   card_tier         : decide SOLO el canal de salida al escalar.
--   archetype         : decide SOLO tono y orden de las ofertas.
--   perfil_crediticio : etiqueta legible del perfil (impecable/mejorable/fatal).
--   categoria         : granularidad fina A1..E (NCB-022).
-- NINGUNA decide si se le ofrece o no una salida: eso sería selección de riesgo.
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
  -- «¿Qué día te pagan?»: lo que respondió la persona (03). Alimenta las
  -- fechas de cobro y cuántas partes se le ofrecen. NULL = aún no respondió.
  frecuencia_pago     VARCHAR2(64),
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
  -- El perfil legible y la categoría no pueden contradecirse.
  CONSTRAINT ck_cliente_coher  CHECK (
       (perfil_crediticio = 'impecable' AND categoria IN ('A1', 'A2'))
    OR (perfil_crediticio = 'mejorable' AND categoria IN ('B', 'C'))
    OR (perfil_crediticio = 'fatal'     AND categoria IN ('D', 'E')))
);

COMMENT ON COLUMN CLIENTE.card_tier IS 'Solo decide el canal de salida al escalar (platino/black -> asesora nombrada).';
COMMENT ON COLUMN CLIENTE.archetype IS 'Solo decide tono y orden de ofertas. Nunca monto ni plazo.';
COMMENT ON COLUMN CLIENTE.password_hash IS 'SHA-256 hex de ruta:<username>:<password>.';

-- Tokens de sesión (antes en memoria: DataStore.tokens).
CREATE TABLE SESION_TOKEN (
  token       VARCHAR2(96) NOT NULL,
  cliente_id  VARCHAR2(64) NOT NULL,
  created_at  TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL,
  expires_at  TIMESTAMP    NOT NULL,
  CONSTRAINT pk_sesion_token PRIMARY KEY (token),
  CONSTRAINT fk_sesion_cli   FOREIGN KEY (cliente_id)
    REFERENCES CLIENTE (id) ON DELETE CASCADE
);

-- Cuentas -> DTO Account. is_primary_source = «de aquí se aparta y de aquí se paga».
CREATE TABLE CUENTA (
  id                 VARCHAR2(64)  NOT NULL,
  cliente_id         VARCHAR2(64)  NOT NULL,
  tipo               VARCHAR2(16)  NOT NULL,
  product_name       VARCHAR2(96)  NOT NULL,
  number_masked      VARCHAR2(24)  NOT NULL,
  number_full        VARCHAR2(32),
  balance_available  NUMBER(14,2)  DEFAULT 0 NOT NULL,
  -- Dinero congelado por un apartado: sigue siendo del cliente, no está disponible.
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

-- Un cliente tiene a lo sumo UNA cuenta origen de apartado/pago.
CREATE UNIQUE INDEX ux_cuenta_primaria
  ON CUENTA (CASE WHEN is_primary_source = 1 THEN cliente_id END);

-- Créditos -> productos bancarios del inicio: tarjeta de crédito (card) y
-- créditos con cuota mensual (personal, hipotecario, bancario).
-- El CHECK ck_credito_forma garantiza que cada fila llene solo las columnas de
-- su tipo, igual que la unión del contrato.
CREATE TABLE CREDITO (
  id                 VARCHAR2(64)  NOT NULL,
  cliente_id         VARCHAR2(64)  NOT NULL,
  kind               VARCHAR2(16)  NOT NULL,
  name               VARCHAR2(96)  NOT NULL,
  number_masked      VARCHAR2(24)  NOT NULL,
  currency           VARCHAR2(8)   DEFAULT 'USD' NOT NULL,
  apartable          NUMBER(1)     DEFAULT 1 NOT NULL,
  -- kind = 'card'
  credit_limit       NUMBER(14,2),
  available          NUMBER(14,2),
  used_pct           NUMBER(3),
  pay_contado        NUMBER(14,2),
  -- kind = 'personal'
  installment_amount NUMBER(14,2),
  current_due_day    NUMBER(2),
  operation_number   VARCHAR2(32),
  -- ciclo de corte y estado (base del push y del resumen de asesoría)
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

-- Productos disponibles que el cliente ya activó (p. ej. el depósito a plazo
-- digital). Al activarse, el producto deja de ofrecerse en el inicio.
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

-- ============================================================================
-- 3. LA RUTA: FECHA DE COBRO, APARTADO Y AUTOPAGO
-- ============================================================================

-- Cambio de fecha de cobro (03 -> 04 -> 05) -> DTO PaymentDatePlan.
-- Un crédito tiene a lo sumo un plan vigente (credito_id es la PK).
CREATE TABLE PLAN_FECHA_COBRO (
  credito_id            VARCHAR2(64) NOT NULL,
  new_day               NUMBER(2)    NOT NULL,
  effective_from_label  VARCHAR2(96) NOT NULL,
  effective_from        DATE,
  amount_unchanged      NUMBER(1)    DEFAULT 1 NOT NULL,
  term_unchanged        NUMBER(1)    DEFAULT 1 NOT NULL,
  frecuencia_id         VARCHAR2(64) NOT NULL,
  opcion_id             VARCHAR2(64) NOT NULL,
  created_at            TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT pk_plan_fecha      PRIMARY KEY (credito_id),
  CONSTRAINT fk_plan_fecha_cred FOREIGN KEY (credito_id)
    REFERENCES CREDITO (id) ON DELETE CASCADE,
  CONSTRAINT fk_plan_fecha_frec FOREIGN KEY (frecuencia_id)
    REFERENCES CATALOGO_FRECUENCIA (id),
  -- «Nunca el 28» (0 = frecuencia semanal, sin día fijo del mes).
  CONSTRAINT ck_plan_fecha_day  CHECK (new_day BETWEEN 0 AND 27),
  -- El producto promete que cambiar la fecha no cambia monto ni plazo.
  CONSTRAINT ck_plan_fecha_inv  CHECK (amount_unchanged = 1 AND term_unchanged = 1)
);

-- Apartar la cuota (06 -> 07 -> 08/08a/08b -> 09) -> DTO ApartadoPlan.
-- APARTAR NO ES ABONAR: el dinero se congela en la cuenta del cliente y se
-- paga completo el día del cobro.
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

-- Un crédito tiene a lo sumo un apartado ACTIVO.
CREATE UNIQUE INDEX ux_apartado_activo
  ON APARTADO (CASE WHEN estado = 'activo' THEN credito_id END);

-- Partes del apartado -> DTO PartInstallment. `fecha` es la verdad; el
-- `dateIso` del contrato se deriva de ella.
CREATE TABLE APARTADO_CUOTA (
  apartado_id VARCHAR2(64) NOT NULL,
  idx         NUMBER(2)    NOT NULL,
  label       VARCHAR2(64) NOT NULL,
  fecha       DATE         NOT NULL,
  amount      NUMBER(14,2) NOT NULL,
  -- pendiente -> apartada (se congeló) | no_alcanzo (no había saldo: choque)
  estado      VARCHAR2(16) DEFAULT 'pendiente' NOT NULL,
  CONSTRAINT pk_apartado_cuota PRIMARY KEY (apartado_id, idx),
  CONSTRAINT ck_apartado_c_est CHECK (estado IN ('pendiente', 'apartada', 'no_alcanzo')),
  CONSTRAINT fk_apartado_cuota FOREIGN KEY (apartado_id)
    REFERENCES APARTADO (id) ON DELETE CASCADE,
  CONSTRAINT ck_apartado_c_idx CHECK (idx BETWEEN 1 AND 4),
  CONSTRAINT ck_apartado_c_amt CHECK (amount > 0)
);

-- Débito automático -> DTO AutopayConfig. Siempre revocable.
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

CREATE UNIQUE INDEX ux_autopago_activo
  ON AUTOPAGO (CASE WHEN active = 1 THEN credito_id END);

-- ============================================================================
-- 4. SEGUIMIENTO: AVISOS Y RÉCORD
-- ============================================================================

-- Avisos (11) -> DTO Notice.
-- REGLA DE CONTENIDO: los avisos CONFIRMAN lo ocurrido y NUNCA recuerdan pagar.
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
  -- Si es accionable debe decir a dónde lleva: cierres nunca ambiguos.
  CONSTRAINT ck_aviso_coher  CHECK ((actionable = 0 AND target IS NULL)
                                 OR (actionable = 1 AND target IS NOT NULL))
);

-- Mi récord (12) -> DTO PaymentRecord. Solo suma; nada como castigo.
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

-- Hitos -> DTO RecordMilestone. Los atrasos viejos se muestran como FECHA DE
-- SALIDA, nunca como castigo.
CREATE TABLE RECORD_HITO (
  cliente_id  VARCHAR2(64) NOT NULL,
  idx         NUMBER(2)    NOT NULL,
  label       VARCHAR2(48) NOT NULL,
  date_label  VARCHAR2(48) NOT NULL,
  CONSTRAINT pk_record_hito PRIMARY KEY (cliente_id, idx),
  CONSTRAINT fk_record_hito FOREIGN KEY (cliente_id)
    REFERENCES CLIENTE (id) ON DELETE CASCADE
);

-- «Qué te está sumando» -> DTO Sumando.
CREATE TABLE RECORD_SUMANDO (
  id          VARCHAR2(64)  NOT NULL,
  cliente_id  VARCHAR2(64)  NOT NULL,
  label       VARCHAR2(200) NOT NULL,
  orden       NUMBER(3)     DEFAULT 0 NOT NULL,
  CONSTRAINT pk_record_sumando PRIMARY KEY (id),
  CONSTRAINT fk_record_sumando FOREIGN KEY (cliente_id)
    REFERENCES CLIENTE (id) ON DELETE CASCADE
);

-- ============================================================================
-- 5. ASESORÍA: CONTEXTO DE CHOQUE, CITAS Y CHAT
-- ============================================================================

-- Contexto de choque -> DTO ShockContext. Nombra el hecho SIN culpar
-- («no alcanzó», nunca «no pagaste»).
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

-- Citas de asistencia -> DTO Appointment. Escalar no es fracaso: es la salida
-- honesta cuando el chat no resuelve.
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

-- Sesión de chat con el asesor IA -> DTO ChatSession.
-- Registro automático exigido por el reto: transcripción (CHAT_MENSAJE),
-- resultado y fecha acordada.
--   resultado: en_curso | acuerdo (con oferta y fecha) | negativa (explícita)
--              | siguiente_paso (concreto) | escalado (asistencia humana)
CREATE TABLE CHAT_SESION (
  id               VARCHAR2(64) NOT NULL,
  cliente_id       VARCHAR2(64) NOT NULL,
  aviso_id         VARCHAR2(64),
  resultado        VARCHAR2(20) DEFAULT 'en_curso' NOT NULL,
  -- Estado del guion: qué se le preguntó (paso) y qué quiere resolver (intencion).
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
  -- Un acuerdo sin fecha sería un cierre ambiguo.
  CONSTRAINT ck_chat_acuerdo    CHECK (resultado <> 'acuerdo' OR fecha_acordada IS NOT NULL),
  CONSTRAINT ck_chat_contadores CHECK (turnos >= 0 AND rechazos >= 0)
);

-- Mensajes -> DTO ChatMessage (transcripción).
--   origen: usuario | modelo (respuesta de LLM) | guion (respuesta determinista)
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

-- Respuestas rápidas ofrecidas en cada mensaje -> DTO ChatQuickReply.
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

-- ============================================================================
-- 6. CICLO DE CORTE, LEDGER, PUSH Y BITÁCORA DE IA
-- ============================================================================

-- Ledger por crédito (reutilizado del esquema anterior). tipo: D = cargo,
-- H = abono. frontera_contada marca un abono que cruzó una frontera de
-- categoría (A_B, B_C, C_D, D_E): insumo de VW_CONTEO_REINCIDENCIA.
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

-- Dispositivos para push (POST /api/devices).
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

-- Bitácora de envíos push. SIMULADO = sin credenciales de Firebase.
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

-- El aviso preventivo de corte no se repite en el mismo ciclo y dispositivo (el
-- job puede correr varias veces al día sin molestar al cliente). Los demás
-- avisos confirman eventos distintos y pueden repetirse en el día.
CREATE UNIQUE INDEX ux_notif_ciclo ON NOTIFICACION_ENVIADA (
  CASE WHEN tipo = 'corte-cercano' AND estado IN ('ENVIADO', 'SIMULADO') THEN credito_id END,
  CASE WHEN tipo = 'corte-cercano' AND estado IN ('ENVIADO', 'SIMULADO') THEN dispositivo_id END,
  CASE WHEN tipo = 'corte-cercano' AND estado IN ('ENVIADO', 'SIMULADO') THEN corte_fecha END
);

-- Bitácora de llamadas a modelos de IA (métricas del dashboard y auditoría de
-- guardrails). guardrail = reglas que tuvo que aplicar la capa de salida.
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

-- ============================================================================
-- 7. FUNCIONES DE CICLO (lógica del esquema anterior) Y VISTAS
-- ============================================================================

-- Próximo corte (>= fecha de referencia). Los días de corte van de 1 a 28, así
-- que el día siempre existe en cualquier mes.
CREATE OR REPLACE FUNCTION FN_CORTE_SIGUIENTE (
  p_dia_corte IN NUMBER,
  p_fecha_ref IN DATE DEFAULT TRUNC(SYSDATE)
) RETURN DATE IS
  v_corte_mes_actual DATE;
BEGIN
  IF p_dia_corte IS NULL THEN
    RETURN NULL;
  END IF;
  v_corte_mes_actual := TRUNC(p_fecha_ref, 'MM') + (p_dia_corte - 1);
  IF v_corte_mes_actual >= TRUNC(p_fecha_ref) THEN
    RETURN v_corte_mes_actual;
  END IF;
  RETURN ADD_MONTHS(TRUNC(p_fecha_ref, 'MM'), 1) + (p_dia_corte - 1);
END FN_CORTE_SIGUIENTE;
/

-- Corte anterior = un mes antes del siguiente.
CREATE OR REPLACE FUNCTION FN_CORTE_ANTERIOR (
  p_dia_corte IN NUMBER,
  p_fecha_ref IN DATE DEFAULT TRUNC(SYSDATE)
) RETURN DATE IS
BEGIN
  RETURN ADD_MONTHS(FN_CORTE_SIGUIENTE(p_dia_corte, p_fecha_ref), -1);
END FN_CORTE_ANTERIOR;
/

-- Antigüedad del crédito en meses (insumo de las ventanas de reincidencia).
CREATE OR REPLACE FUNCTION FN_ANTIGUEDAD_MESES (
  p_credito_id IN VARCHAR2,
  p_fecha_ref  IN DATE DEFAULT TRUNC(SYSDATE)
) RETURN NUMBER IS
  v_apertura CREDITO.fecha_apertura%TYPE;
BEGIN
  SELECT fecha_apertura INTO v_apertura FROM CREDITO WHERE id = p_credito_id;
  RETURN TRUNC(MONTHS_BETWEEN(p_fecha_ref, v_apertura));
EXCEPTION
  WHEN NO_DATA_FOUND THEN RETURN 0;
END FN_ANTIGUEDAD_MESES;
/

-- Saldo del ciclo vigente y días que faltan para el corte, por crédito.
CREATE OR REPLACE VIEW VW_SALDO_CICLO AS
SELECT c.id                                                          AS credito_id,
       c.cliente_id                                                  AS cliente_id,
       cl.display_name                                               AS cliente,
       c.kind                                                        AS kind,
       c.number_masked                                               AS number_masked,
       c.dia_corte                                                   AS dia_corte,
       FN_CORTE_ANTERIOR(c.dia_corte)                                AS corte_anterior,
       FN_CORTE_SIGUIENTE(c.dia_corte)                               AS corte_siguiente,
       NVL(SUM(CASE WHEN t.tipo = 'D' THEN t.monto END), 0)          AS total_debe,
       NVL(SUM(CASE WHEN t.tipo = 'H' THEN t.monto END), 0)          AS total_haber,
       NVL(SUM(CASE WHEN t.tipo = 'D' THEN t.monto ELSE -t.monto END), 0) AS saldo_pendiente,
       TRUNC(FN_CORTE_SIGUIENTE(c.dia_corte)) - TRUNC(SYSDATE)       AS dias_para_corte
  FROM CREDITO c
  JOIN CLIENTE cl ON cl.id = c.cliente_id AND cl.activo = 1
  LEFT JOIN TRANSACCION t
         ON t.credito_id = c.id
        AND t.fecha >  FN_CORTE_ANTERIOR(c.dia_corte)
        AND t.fecha <= FN_CORTE_SIGUIENTE(c.dia_corte)
 GROUP BY c.id, c.cliente_id, cl.display_name, c.kind, c.number_masked, c.dia_corte;

-- Reincidencia por frontera de categoría (lógica del esquema anterior).
-- Ventanas y tolerancias: A_B 6/24 meses según antigüedad (1/2 incidencias),
-- B_C 12 meses (1), C_D 6 meses (0), D_E sin ventana (0).
CREATE OR REPLACE VIEW VW_CONTEO_REINCIDENCIA AS
WITH incidencias AS (
  SELECT t.credito_id,
         t.frontera_contada AS frontera,
         t.fecha            AS fecha_incidencia,
         CASE t.frontera_contada
           WHEN 'A_B' THEN CASE WHEN FN_ANTIGUEDAD_MESES(t.credito_id) <= 6 THEN 6 ELSE 24 END
           WHEN 'B_C' THEN 12
           WHEN 'C_D' THEN 6
           WHEN 'D_E' THEN NULL
         END AS ventana_meses,
         CASE t.frontera_contada
           WHEN 'A_B' THEN CASE WHEN FN_ANTIGUEDAD_MESES(t.credito_id) <= 6 THEN 1 ELSE 2 END
           WHEN 'B_C' THEN 1
           WHEN 'C_D' THEN 0
           WHEN 'D_E' THEN 0
         END AS incidencias_toleradas
    FROM TRANSACCION t
   WHERE t.tipo = 'H'
     AND t.frontera_contada IS NOT NULL
)
SELECT credito_id,
       frontera,
       MAX(ventana_meses)                                               AS ventana_meses,
       MAX(incidencias_toleradas)                                       AS incidencias_toleradas,
       COUNT(*)                                                         AS incidencias_en_ventana,
       CASE WHEN COUNT(*) > MAX(incidencias_toleradas) THEN 1 ELSE 0 END AS debe_bajar
  FROM incidencias
 WHERE ventana_meses IS NULL
    OR fecha_incidencia > ADD_MONTHS(TRUNC(SYSDATE), -ventana_meses)
 GROUP BY credito_id, frontera;

-- Candidatos al push preventivo: saldo pendiente en el ciclo y corte por venir.
-- El job filtra dias_para_corte BETWEEN 1 AND PUSH_DIAS_AVISO (default 5).
-- A propósito NO incluye perfil ni categoría: el aviso se decide por el ciclo,
-- nunca por el riesgo del cliente.
CREATE OR REPLACE VIEW VW_PUSH_CANDIDATOS AS
SELECT v.credito_id,
       v.cliente_id,
       cl.first_name,
       v.kind,
       v.number_masked,
       v.dia_corte,
       v.corte_siguiente,
       v.dias_para_corte,
       v.saldo_pendiente,
       d.id          AS dispositivo_id,
       d.push_token  AS push_token,
       d.platform    AS platform
  FROM VW_SALDO_CICLO v
  JOIN CLIENTE cl ON cl.id = v.cliente_id
  LEFT JOIN DISPOSITIVO d ON d.cliente_id = v.cliente_id AND d.activo = 1
 WHERE v.saldo_pendiente > 0
   AND v.dias_para_corte >= 0;

-- Métricas agregadas por perfil y categoría para el dashboard de gestión.
CREATE OR REPLACE VIEW VW_DASHBOARD_PERFIL AS
SELECT perfil_crediticio,
       categoria,
       COUNT(*)                     AS clientes,
       SUM(apartados_activos)       AS apartados_activos,
       SUM(citas_agendadas)         AS citas_agendadas,
       SUM(chats)                   AS chats,
       SUM(chats_escalados)         AS chats_escalados,
       SUM(chats_resueltos)         AS chats_resueltos
  FROM (
    SELECT cl.id,
           cl.perfil_crediticio,
           cl.categoria,
           (SELECT COUNT(*) FROM APARTADO a JOIN CREDITO cr ON cr.id = a.credito_id
             WHERE cr.cliente_id = cl.id AND a.estado = 'activo')                        AS apartados_activos,
           (SELECT COUNT(*) FROM CITA ci
             WHERE ci.cliente_id = cl.id AND ci.status = 'scheduled')                    AS citas_agendadas,
           (SELECT COUNT(*) FROM CHAT_SESION cs WHERE cs.cliente_id = cl.id)             AS chats,
           (SELECT COUNT(*) FROM CHAT_SESION cs
             WHERE cs.cliente_id = cl.id AND cs.resultado = 'escalado')                  AS chats_escalados,
           (SELECT COUNT(*) FROM CHAT_SESION cs
             WHERE cs.cliente_id = cl.id
               AND cs.resultado IN ('acuerdo', 'negativa', 'siguiente_paso'))            AS chats_resueltos
      FROM CLIENTE cl
     WHERE cl.activo = 1
  )
 GROUP BY perfil_crediticio, categoria;

-- ============================================================================
-- 8. ÍNDICES DE APOYO
-- ============================================================================
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

-- ============================================================================
-- 9. VERIFICACIÓN
-- ============================================================================
DECLARE
  v_tablas    NUMBER;
  v_vistas    NUMBER;
  v_funcs     NUMBER;
  v_invalidos NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_tablas FROM user_tables;
  SELECT COUNT(*) INTO v_vistas FROM user_views;
  SELECT COUNT(*) INTO v_funcs  FROM user_objects WHERE object_type = 'FUNCTION';
  SELECT COUNT(*) INTO v_invalidos FROM user_objects WHERE status <> 'VALID';

  DBMS_OUTPUT.PUT_LINE('== Esquema Ruta creado ==');
  DBMS_OUTPUT.PUT_LINE('Tablas    : ' || v_tablas || ' (esperadas 36)');
  DBMS_OUTPUT.PUT_LINE('Vistas    : ' || v_vistas || ' (esperadas 4)');
  DBMS_OUTPUT.PUT_LINE('Funciones : ' || v_funcs  || ' (esperadas 3)');
  DBMS_OUTPUT.PUT_LINE('Invalidos : ' || v_invalidos);
END;
/
