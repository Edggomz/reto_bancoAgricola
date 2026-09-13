-- ============================================================================
-- Ruta · Bancoagrícola — Creación del usuario/esquema de la aplicación
-- ============================================================================
-- Se ejecuta UNA vez con autenticación de Windows. NO necesita la contraseña de
-- SYS ni de SYSTEM: basta con que tu usuario de Windows esté en el grupo ORA_DBA
-- (el instalador de Oracle lo agrega). Desde PowerShell o CMD:
--
--   sqlplus / as sysdba @C:\Users\ferme\Desktop\BackEnd-bancoAgricola\src\main\resources\db\00-crear-usuario.sql
--
-- Qué hace:
--   1. Abre el PDB XEPDB1 si estuviera cerrado, deja guardado que abra solo al
--      reiniciar la PC, y entra en él (el esquema de la app no va en el CDB raíz).
--   2. Te pide la contraseña de RUTA_APP y la oculta al escribirla (HIDE).
--   3. Crea un perfil propio cuya contraseña NO vence. Con el perfil DEFAULT
--      vence a los 180 días y el backend dejaría de conectar con ORA-28001.
--   4. Crea RUTA_APP con los privilegios mínimos. Si ya existía, lo borra con
--      todo su esquema para empezar limpio.
--
-- Contraseña: usa solo letras, números y guion bajo (12 caracteres o más).
-- Evita comillas, @, $, & y espacios: rompen la conexión de SQL*Plus, el .env
-- o la terminal.
-- ============================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE
SET DEFINE ON
SET VERIFY OFF
SET FEEDBACK OFF
SET SERVEROUTPUT ON

-- 1. PDB abierto, persistente y seleccionado ---------------------------------
DECLARE
  v_modo VARCHAR2(20);
BEGIN
  SELECT open_mode INTO v_modo FROM v$pdbs WHERE name = 'XEPDB1';
  IF v_modo = 'MOUNTED' THEN
    EXECUTE IMMEDIATE 'ALTER PLUGGABLE DATABASE XEPDB1 OPEN';
    DBMS_OUTPUT.PUT_LINE('XEPDB1 estaba cerrado; se abrio.');
  END IF;
END;
/

ALTER PLUGGABLE DATABASE XEPDB1 SAVE STATE;
ALTER SESSION SET CONTAINER = XEPDB1;

-- 2. Contraseña (oculta) ------------------------------------------------------
PROMPT
ACCEPT app_pass CHAR PROMPT 'Contrasena para el usuario RUTA_APP: ' HIDE

-- 3. Perfil sin vencimiento de contraseña ------------------------------------
-- Solo cambia la vigencia; el resto de límites (intentos fallidos, etc.) hereda
-- los del perfil DEFAULT.
DECLARE
  v_existe NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_existe FROM dba_profiles WHERE profile = 'RUTA_APP_PROFILE';
  IF v_existe = 0 THEN
    EXECUTE IMMEDIATE 'CREATE PROFILE RUTA_APP_PROFILE LIMIT PASSWORD_LIFE_TIME UNLIMITED';
  ELSE
    EXECUTE IMMEDIATE 'ALTER PROFILE RUTA_APP_PROFILE LIMIT PASSWORD_LIFE_TIME UNLIMITED';
  END IF;
END;
/

-- 4. Usuario de la aplicación -------------------------------------------------
DECLARE
  v_existe NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_existe FROM dba_users WHERE username = 'RUTA_APP';
  IF v_existe > 0 THEN
    EXECUTE IMMEDIATE 'DROP USER RUTA_APP CASCADE';
    DBMS_OUTPUT.PUT_LINE('Usuario RUTA_APP anterior eliminado.');
  END IF;
END;
/

CREATE USER RUTA_APP IDENTIFIED BY "&app_pass"
  DEFAULT TABLESPACE USERS
  PROFILE RUTA_APP_PROFILE;

UNDEFINE app_pass

GRANT CREATE SESSION   TO RUTA_APP;
GRANT CREATE TABLE     TO RUTA_APP;
GRANT CREATE VIEW      TO RUTA_APP;
GRANT CREATE PROCEDURE TO RUTA_APP;
GRANT CREATE SEQUENCE  TO RUTA_APP;
GRANT CREATE TRIGGER   TO RUTA_APP;
ALTER USER RUTA_APP QUOTA UNLIMITED ON USERS;

-- Verificación ----------------------------------------------------------------
COLUMN username       FORMAT A10
COLUMN account_status FORMAT A14
COLUMN profile        FORMAT A18
COLUMN vence          FORMAT A8
SELECT username, account_status, profile,
       NVL(TO_CHAR(expiry_date, 'YYYY-MM-DD'), 'nunca') AS vence
  FROM dba_users
 WHERE username = 'RUTA_APP';

PROMPT
PROMPT == Usuario RUTA_APP creado ==
PROMPT Cadena JDBC para el .env:  jdbc:oracle:thin:@localhost:1521/XEPDB1
PROMPT

EXIT
