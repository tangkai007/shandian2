// 工程内部 import 的「未使用」启发式检测（Dart 工具链缺失时替代 flutter analyze 的一部分）。
// 对 lib/ 与 test/ 中所有相对导入 / package:shandian_live 导入，提取被导入文件的顶层标识符，
// 若在导入方文件中均未出现，则判定为疑似未使用。
// 用法: node tools/check_unused_imports.mjs <repoRoot>
import { readdirSync, readFileSync, statSync, existsSync } from 'node:fs';
import { join, dirname, resolve, relative } from 'node:path';

const root = resolve(process.argv[2] ?? '.');
const libRoot = join(root, 'app', 'lib');
const pkg = 'package:shandian_live/';

function walk(dir, out = []) {
  if (!existsSync(dir)) return out;
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) walk(p, out);
    else if (name.endsWith('.dart')) out.push(p);
  }
  return out;
}

const files = [...walk(libRoot), ...walk(join(root, 'app', 'test'))];

function topLevelSymbols(src) {
  const out = new Set();
  const patterns = [
    /\bclass\s+([A-Za-z_]\w*)/g,
    /\benum\s+([A-Za-z_]\w*)/g,
    /\btypedef\s+([A-Za-z_]\w*)/g,
    /\bmixin\s+([A-Za-z_]\w*)/g,
    /\bextension\s+([A-Za-z_]\w*)\s+on\b/g,
    /^\s*(?:final|const|var)\s+[A-Za-z_][\w<>, ?]*\s+([A-Za-z_]\w*)\s*=/gm,
    /^[A-Za-z_][\w<>?, ]*\s+([A-Za-z_]\w*)\s*\([^)]*\)\s*(?:async\s*)?\{/gm,
  ];
  for (const re of patterns) for (const m of src.matchAll(re)) out.add(m[1]);
  return out;
}

const symbolsCache = new Map();
function symbolsOf(file) {
  if (!symbolsCache.has(file)) symbolsCache.set(file, topLevelSymbols(readFileSync(file, 'utf8')));
  return symbolsCache.get(file);
}

const importRe = /^\s*import\s+(?:'([^']+)'|"([^"]+)")/gm;
let problems = 0;

for (const file of files) {
  const src = readFileSync(file, 'utf8');
  const withoutImports = src.replace(importRe, '');
  for (const m of src.matchAll(importRe)) {
    const spec = m[1] ?? m[2];
    let target = null;
    if (spec.startsWith(pkg)) target = join(libRoot, spec.slice(pkg.length));
    else if (spec.startsWith('.')) target = resolve(dirname(file), spec);
    else continue;
    if (!existsSync(target)) continue; // 交由 check_dart.mjs 报缺失
    const symbols = symbolsOf(target);
    if (symbols.size === 0) continue; // 无法判定（纯 barrel / 仅 typedef）
    const used = [...symbols].some((s) => new RegExp(`\\b${s}\\b`).test(withoutImports));
    if (!used) {
      console.log(`UNUSED? ${relative(root, file)} -> ${spec}  (symbols: ${[...symbols].join(', ')})`);
      problems++;
    }
  }
}

console.log(`\nscanned ${files.length} files, ${problems} suspicious import(s)`);
