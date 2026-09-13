// Recibe archivos exportados desde Figma (vía el plugin Desktop Bridge) y los
// guarda dentro del proyecto. El plugin solo puede hablar con localhost:9223-9232,
// por eso escucha en 9230.
//
//   node scripts/figma-receiver.mjs
//   (en figma_execute) await fetch('http://localhost:9230/save?path=design/reference/02.png',
//                                  { method: 'POST', body: bytes })
import { createServer } from 'node:http';
import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, resolve, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const PORT = Number(process.env.PORT ?? 9230);

const cors = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
  'Access-Control-Allow-Headers': '*',
};

createServer(async (req, res) => {
  if (req.method === 'OPTIONS') return res.writeHead(204, cors).end();
  const url = new URL(req.url, `http://localhost:${PORT}`);
  const rel = url.searchParams.get('path');
  const target = rel ? resolve(ROOT, rel) : null;
  if (req.method !== 'POST' || url.pathname !== '/save' || !target || relative(ROOT, target).startsWith('..')) {
    return res.writeHead(400, cors).end('bad request');
  }
  const chunks = [];
  for await (const c of req) chunks.push(c);
  const body = Buffer.concat(chunks);
  await mkdir(dirname(target), { recursive: true });
  await writeFile(target, body);
  console.log(`${body.length} B → ${rel}`);
  res.writeHead(200, cors).end(JSON.stringify({ ok: true, bytes: body.length }));
}).listen(PORT, () => console.log(`figma-receiver en http://localhost:${PORT} → ${ROOT}`));
