// Plays the app for a translated extension: network (with a cookie store, like URLSession), settings,
// shared resources and crypto. Usage: bun host.mjs <extension.js> [query]
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const here = path.dirname(new URL(import.meta.url).pathname);
const resources = {
  'org/threeten/bp/TZDB.dat': path.join(here, 'res/TZDB.dat'),
  'okhttp3/internal/publicsuffix/PublicSuffixDatabase.list': path.join(here, 'res/PublicSuffixDatabase.list'),
};
const prefs = {};
const cookies = new Map(); // host -> Map(name -> value)
const stats = { requests: 0, netMs: 0, bytes: 0 };

function cookieHeader(url) {
  const host = new URL(url).hostname;
  const parts = [];
  for (const [h, jar] of cookies) if (host === h || host.endsWith('.' + h)) for (const [k, v] of jar) parts.push(`${k}=${v}`);
  return parts.join('; ');
}
function storeCookies(url, res) {
  const host = new URL(url).hostname;
  for (const line of res.headers.getSetCookie?.() ?? []) {
    const [pair, ...attrs] = line.split(';');
    const eq = pair.indexOf('=');
    if (eq < 0) continue;
    let domain = host;
    for (const a of attrs) { const [k, v] = a.trim().split('='); if (k.toLowerCase() === 'domain' && v) domain = v.replace(/^\./, ''); }
    if (!cookies.has(domain)) cookies.set(domain, new Map());
    cookies.get(domain).set(pair.slice(0, eq).trim(), pair.slice(eq + 1).trim());
  }
}

// Images: @napi-rs/canvas (Skia) stands in for ImageIO/CoreGraphics. Optional: without it, image calls fail.
let skia = null;
try { skia = await import('@napi-rs/canvas'); } catch {}
const images = new Map();
const deliveries = new Map();
let nextDelivery = 1;
let nextImage = 1;
const css = (c) => `rgba(${(c >> 16) & 255},${(c >> 8) & 255},${c & 255},${((c >>> 24) & 255) / 255})`;
function ctxFor(id, m) {
  const ctx = images.get(id).getContext('2d');
  ctx.setTransform(m[0], m[1], m[2], m[3], m[4], m[5]);
  return ctx;
}
function font(f) { return `${f.bold ? 'bold ' : ''}${f.size}px ${f.family}`; }
const imageOps = {
  decode(a, data) {
    const img = new skia.Image(); img.src = Buffer.from(u8(data));
    const c = skia.createCanvas(img.width, img.height); c.getContext('2d').drawImage(img, 0, 0);
    const id = nextImage++; images.set(id, c); return `${id},${img.width},${img.height}`;
  },
  create(a) { const id = nextImage++; images.set(id, skia.createCanvas(a.w, a.h)); return id; },
  free(a) { images.delete(a.id); return null; },
  draw(a) {
    const ctx = ctxFor(a.id, a.m);
    ctx.drawImage(images.get(a.src), a.s[0], a.s[1], a.s[2], a.s[3], a.d[0], a.d[1], a.d[2], a.d[3]);
    return null;
  },
  fill(a) { const c = images.get(a.id); const ctx = ctxFor(a.id, a.m); ctx.fillStyle = css(a.color); ctx.fillRect(0, 0, c.width, c.height); return null; },
  shape(a) {
    const ctx = ctxFor(a.id, a.m); const [l, t, r, b, rx] = a.r;
    ctx.beginPath(); if (rx) ctx.roundRect(l, t, r - l, b - t, rx); else ctx.rect(l, t, r - l, b - t);
    if (a.style !== 'STROKE') { ctx.fillStyle = css(a.color); ctx.fill(); }
    if (a.style !== 'FILL') { ctx.strokeStyle = css(a.color); ctx.lineWidth = a.stroke || 1; ctx.stroke(); }
    return null;
  },
  text(a) {
    const ctx = ctxFor(a.id, a.m); ctx.font = font(a.font);
    ctx.textAlign = { LEFT: 'left', CENTER: 'center', RIGHT: 'right' }[a.align] || 'left';
    if (a.style === 'STROKE') { ctx.strokeStyle = css(a.color); ctx.lineWidth = a.stroke || 1; ctx.strokeText(a.text, a.x, a.y); }
    else { ctx.fillStyle = css(a.color); ctx.fillText(a.text, a.x, a.y); }
    return null;
  },
  measure(a) {
    const ctx = (images.get(0) ?? (images.set(0, skia.createCanvas(1, 1)), images.get(0))).getContext('2d');
    ctx.font = font(a.font); return ctx.measureText(a.text).width * (a.font.scaleX || 1);
  },
};
const imageByteOps = {
  encode(a) { const c = images.get(a.id); return i8(c.toBuffer(a.format === 'png' ? 'image/png' : a.format === 'webp' ? 'image/webp' : 'image/jpeg', a.quality)); },
  read(a) { return i8(Buffer.from(images.get(a.id).getContext('2d').getImageData(a.x, a.y, a.w, a.h).data.buffer)); },
};

// A site's script (QuickJS in Mihon) runs in its own global scope, like a fresh QuickJS context.
import vm from 'node:vm';
function evaluate(script) {
  try {
    const v = vm.runInNewContext(script, {}, { timeout: 10000 });
    const value = typeof v === 'string' || typeof v === 'number' || typeof v === 'boolean' || v == null ? v ?? null
      : Array.isArray(v) ? v.map((x) => (typeof x === 'object' ? null : x)) : null;
    return JSON.stringify({ value });
  } catch (e) { return JSON.stringify({ error: String(e) }); }
}

function u8(a) { return a ? new Uint8Array(a.buffer, a.byteOffset, a.byteLength) : null; }
function i8(b) { return new Int8Array(b.buffer, b.byteOffset, b.byteLength); }

globalThis.exia = {
  fetch(meta, body, done) {
    const m = JSON.parse(meta);
    const headers = new Headers();
    for (const [k, v] of m.headers) headers.append(k, v);
    if (!headers.has('cookie')) { const c = cookieHeader(m.url); if (c) headers.set('cookie', c); }
    const t0 = performance.now();
    stats.requests++;
    (async () => {
      let url = m.url, method = m.method, reqBody = body ? Buffer.from(u8(body)) : undefined, res;
      for (let hop = 0; hop < 10; hop++) {
        res = await fetch(url, { method, headers, body: reqBody, redirect: 'manual' });
        storeCookies(url, res);
        if (m.followRedirects && [301, 302, 303, 307, 308].includes(res.status) && res.headers.get('location')) {
          url = new URL(res.headers.get('location'), url).toString();
          if (res.status === 303 || ((res.status === 301 || res.status === 302) && method === 'POST')) { method = 'GET'; reqBody = undefined; }
          const c = cookieHeader(url); if (c) headers.set('cookie', c);
          continue;
        }
        break;
      }
      const buf = new Uint8Array(await res.arrayBuffer());
      stats.netMs += performance.now() - t0;
      stats.bytes += buf.length;
      if (process.env.SAVE_PAGES && (res.headers.get('content-type') || '').includes('html') && buf.length > 20000) {
        fs.mkdirSync(process.env.SAVE_PAGES, { recursive: true });
        fs.writeFileSync(path.join(process.env.SAVE_PAGES, `${new URL(url).hostname}-${stats.requests}.html`), buf);
      }
      const out = [];
      res.headers.forEach((v, k) => { if (k !== 'content-encoding' && k !== 'content-length' && k !== 'set-cookie') out.push([k, v]); });
      for (const c of res.headers.getSetCookie?.() ?? []) out.push(['set-cookie', c]);
      done(JSON.stringify({ status: res.status, statusText: res.statusText, url, headers: out }), i8(buf), null);
    })().catch((e) => done(null, null, String(e)));
  },
  evaluate,
  deliver(bytes) { const key = 'd' + (nextDelivery++); deliveries.set(key, Buffer.from(u8(bytes))); return key; },
  cookieGet: (url) => cookieHeader(url) || null,
  cookieSet(url, cookie) {
    const host = new URL(url).hostname; const [pair, ...attrs] = cookie.split(';'); const eq = pair.indexOf('=');
    if (eq < 0) return;
    let domain = host;
    for (const a of attrs) { const [k, v] = a.trim().split('='); if (k.toLowerCase() === 'domain' && v) domain = v.replace(/^\./, ''); }
    if (!cookies.has(domain)) cookies.set(domain, new Map());
    cookies.get(domain).set(pair.slice(0, eq).trim(), pair.slice(eq + 1).trim());
  },
  image(op, args, data) { if (!skia) throw new Error('no image engine'); const t0 = performance.now(); try { return imageOps[op](JSON.parse(args), data); } finally { stats.imageMs = (stats.imageMs || 0) + performance.now() - t0; } },
  imageBytes(op, args, data) { if (!skia) throw new Error('no image engine'); const t0 = performance.now(); try { return imageByteOps[op](JSON.parse(args), data); } finally { stats.imageMs = (stats.imageMs || 0) + performance.now() - t0; } },
  prefGet: (k) => prefs[k],
  prefSet: (k, v) => { if (v == null) delete prefs[k]; else prefs[k] = v; },
  log: (level, message) => { if (process.env.VERBOSE) console.error(`[log ${level}]`, message); },
  resource: (name) => (resources[name] ? i8(fs.readFileSync(resources[name])) : null),
  crypto(op, transformation, key, iv, data, extra) {
    key = key && Buffer.from(u8(key)); iv = iv && Buffer.from(u8(iv)); data = data && Buffer.from(u8(data));
    if (op === 'digest') return i8(crypto.createHash({ SHA1: 'sha1', SHA256: 'sha256', SHA384: 'sha384', SHA512: 'sha512' }[transformation]).update(data).digest());
    if (op === 'hmac') return i8(crypto.createHmac(transformation.replace(/^Hmac/i, '').toLowerCase(), key).update(data).digest());
    if (op === 'cipher') {
      const x = JSON.parse(extra);
      const [alg, mode, padding] = transformation.toUpperCase().split('/');
      if (alg !== 'AES') throw new Error('cipher ' + transformation);
      const name = `aes-${key.length * 8}-${(mode || 'ECB').toLowerCase()}`;
      const decrypt = x.mode === 2;
      if (mode === 'GCM') {
        const tagLen = x.tagBits / 8;
        if (decrypt) {
          const d = crypto.createDecipheriv(name, key, iv, { authTagLength: tagLen });
          d.setAuthTag(data.subarray(data.length - tagLen));
          if (x.aad) d.setAAD(Buffer.from(x.aad, 'base64'));
          return i8(Buffer.concat([d.update(data.subarray(0, data.length - tagLen)), d.final()]));
        }
        const c = crypto.createCipheriv(name, key, iv, { authTagLength: tagLen });
        if (x.aad) c.setAAD(Buffer.from(x.aad, 'base64'));
        return i8(Buffer.concat([c.update(data), c.final(), c.getAuthTag()]));
      }
      const c = (decrypt ? crypto.createDecipheriv : crypto.createCipheriv)(name, key, mode === 'ECB' ? null : iv);
      c.setAutoPadding(!(padding || '').startsWith('NO'));
      return i8(Buffer.concat([c.update(data), c.final()]));
    }
    throw new Error('crypto ' + op);
  },
};

export async function load(file) {
  const t0 = performance.now();
  const src = fs.readFileSync(file, 'utf8');
  const mod = {};
  new Function('exports', src)(mod);
  mod.main([]);
  return performance.now() - t0;
}

export function call(req) {
  return new Promise((resolve) => globalThis.exiaCall(JSON.stringify(req), (r) => resolve(JSON.parse(r))));
}

export { stats };

if (import.meta.main || process.argv[1] === new URL(import.meta.url).pathname) {
  const file = process.argv[2];
  const query = process.argv[3] || 'a';
  const report = { file: path.basename(file), size: fs.statSync(file).size };
  const timed = async (label, req) => {
    const n0 = stats.netMs, r0 = stats.requests;
    const t0 = performance.now();
    const r = await call(req);
    const ms = performance.now() - t0;
    report[label] = { ms: +ms.toFixed(1), cpuMs: +(ms - (stats.netMs - n0)).toFixed(1), requests: stats.requests - r0, ...(r.error ? { error: r.error } : {}) };
    return r.ok;
  };
  report.loadMs = +(await load(file)).toFixed(1);
  const sources = (await call({ method: 'sources' })).ok;
  report.sources = sources?.map((s) => `${s.name} (${s.lang})`);
  const es = sources?.find((s) => s.lang === 'es') ?? sources?.[0];
  if (es) {
    const src = es.id;
    const popular = await timed('popular', { method: 'popular', source: src, page: 1 });
    report.popular.count = popular?.mangas?.length;
    const search = await timed('search', { method: 'search', source: src, page: 1, query });
    if (report.search) report.search.count = search?.mangas?.length;
    const manga = popular?.mangas?.[0] ?? search?.mangas?.[0];
    if (manga) {
      report.title = manga.title;
      const details = await timed('details', { method: 'details', source: src, manga });
      // Like the app: details fill in the stored title, whose URL stays the one browsing gave.
      const chapters = await timed('chapters', { method: 'chapters', source: src, manga: { ...manga, ...(details ?? {}), url: manga.url, memo: details?.memo ?? manga.memo } });
      report.chapters.count = chapters?.length;
      const chapter = chapters?.[0];
      if (chapter) {
        const pages = await timed('pages', { method: 'pages', source: src, chapter });
        report.pages.count = pages?.length;
        if (pages?.[0]) {
          const img = await timed('image', { method: 'imageData', source: src, page: pages[0] });
          if (img) {
            report.image.status = img.status; report.image.bytes = img.bytes; report.image.type = img.type;
            const bytes = deliveries.get(img.data) ?? Buffer.alloc(0); deliveries.delete(img.data);
            const head = bytes.subarray(0, 16);
            report.image.isImage = head[0] === 0xff || head[0] === 0x89 || head.subarray(0, 4).toString() === 'RIFF' || head.subarray(0, 3).toString() === 'GIF' || head.subarray(4, 8).toString() === 'ftyp';
            if (process.env.SAVE_IMAGES) fs.writeFileSync(path.join(process.env.SAVE_IMAGES, report.file + '.img'), bytes);
          }
        }
      }
    }
  }
  const mem = process.memoryUsage();
  report.rssMB = +(mem.rss / 1048576).toFixed(1);
  report.heapMB = +(mem.heapUsed / 1048576).toFixed(1);
  report.imageMs = +(stats.imageMs || 0).toFixed(1);
  console.log(JSON.stringify(report));
  process.exit(0);
}
