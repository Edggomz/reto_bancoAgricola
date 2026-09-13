// Deriva src/main/resources/db/h2/schema-h2.sql del esquema Oracle definitivo.
//   node tools/seed/oracle-to-h2.mjs
//
// H2 corre en MODE=Oracle, así que VARCHAR2/NUMBER/CLOB/SYSDATE/SYSTIMESTAMP se
// aceptan tal cual. Lo que no existe en H2 se omite: bloques PL/SQL, funciones,
// vistas (usan las funciones) e índices únicos sobre expresiones CASE. Esa
// lógica vive en Java (service/CalendarioPagos, RutaMotor), que es la misma para
// Oracle y H2; las vistas de Oracle quedan como apoyo para análisis SQL.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const ORIGEN = resolve(ROOT, 'src/main/resources/db/oracle-schema.sql');
const DESTINO = resolve(ROOT, 'src/main/resources/db/h2/schema-h2.sql');

const fuente = readFileSync(ORIGEN, 'utf8');
// Bloques PL/SQL (DECLARE/BEGIN/CREATE OR REPLACE FUNCTION...) terminan en una
// línea con '/'; el resto de sentencias terminan en ';'.
const bloques = [];
let actual = [];
let plsql = false;
for (const linea of fuente.split(/\r?\n/)) {
  const t = linea.trim().toUpperCase();
  // Comandos de SQL*Plus (sin ';'): no existen en H2 y no forman parte de ninguna sentencia.
  if (!plsql && /^(SET |PROMPT\b|WHENEVER\b|EXIT\b)/.test(t)) continue;
  if (!plsql && actual.every((l) => /^\s*(--.*)?$/.test(l)) && /^(DECLARE|BEGIN|CREATE OR REPLACE (FUNCTION|PROCEDURE|TRIGGER))\b/.test(t)) {
    plsql = true;
  }
  if (plsql) {
    if (t === '/') { bloques.push({ plsql: true, texto: actual.join('\n') }); actual = []; plsql = false; }
    else actual.push(linea);
    continue;
  }
  actual.push(linea);
  if (/;\s*$/.test(linea) && !/^\s*--/.test(linea)) { bloques.push({ plsql: false, texto: actual.join('\n') }); actual = []; }
}
if (actual.join('').trim()) bloques.push({ plsql: false, texto: actual.join('\n') });

const salida = [
  '-- ============================================================================',
  '-- Ruta · Bancoagrícola — ESQUEMA H2 (perfil por defecto, MODE=Oracle)',
  '-- ============================================================================',
  '-- GENERADO por tools/seed/oracle-to-h2.mjs desde db/oracle-schema.sql. No editar',
  '-- a mano: cambiar el esquema Oracle y volver a ejecutar el script.',
  '-- Se omiten funciones PL/SQL, vistas e índices sobre expresiones: esa lógica',
  '-- vive en Java y es la misma en Oracle y en H2.',
  '-- ============================================================================',
  '',
];
let tablas = 0;
for (const bloque of bloques) {
  if (bloque.plsql) continue;
  const sinComentarios = bloque.texto.split('\n').filter((l) => !/^\s*--/.test(l)).join('\n').trim();
  if (!sinComentarios) continue;
  const inicio = sinComentarios.toUpperCase();
  if (inicio.startsWith('SET ') || inicio.startsWith('PROMPT') || inicio.startsWith('WHENEVER')) continue;
  if (inicio.startsWith('CREATE OR REPLACE VIEW')) continue;
  if (/^CREATE UNIQUE INDEX[\s\S]*\(\s*CASE/i.test(sinComentarios)) continue;
  if (inicio.startsWith('CREATE TABLE')) tablas++;
  salida.push(sinComentarios, '');
}
mkdirSync(dirname(DESTINO), { recursive: true });
writeFileSync(DESTINO, salida.join('\n'), 'utf8');
console.log(`schema-h2.sql generado: ${tablas} tablas -> ${DESTINO}`);
