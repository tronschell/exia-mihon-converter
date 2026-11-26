// Times bench.js (jsoup translated to JavaScript) on each page. Usage: bun bench.mjs out/bench.js page.html...
import fs from 'node:fs';
const mod = {};
new Function('exports', fs.readFileSync(process.argv[2], 'utf8'))(mod);
mod.main([]);
const n = +(process.env.N || 20);
for (const page of process.argv.slice(3)) {
  const html = fs.readFileSync(page, 'utf8');
  mod.run(html, 3);
  console.log(`${page}\t${mod.run(html, n)}`);
}
