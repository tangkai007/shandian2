// 静态自检（Dart 工具链缺失时的替代性检查）：
//  1) 解析所有 import，验证工程内相对导入与 package:shandian_live 导入是否指向真实文件；
//  2) 校验括号 / 引号 / 注释基本配平（能捕捉明显的语法残缺）。
// 用法: node tools/check_dart.mjs <repoRoot>
import { readdirSync, readFileSync, statSync, existsSync } from 'node:fs';
import { join, dirname, resolve, relative } from 'node:path';

const root = resolve(process.argv[2] ?? '.');
const libRoot = join(root, 'app', 'lib');
const pkg = 'package:shandian_live/';

function walk(dir, out = []) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) walk(p, out);
    else if (name.endsWith('.dart')) out.push(p);
  }
  return out;
}

const files = [...walk(libRoot), ...walk(join(root, 'app', 'test'))];
let errors = 0;

// --- 1) imports ---
const importRe = /^\s*import\s+'([^']+)'(?:\s+as\s+\w+)?\s*;/gm;
for (const file of files) {
  const src = readFileSync(file, 'utf8');
  for (const m of src.matchAll(importRe)) {
    const spec = m[1];
    let target = null;
    if (spec.startsWith(pkg)) target = join(libRoot, spec.slice(pkg.length));
    else if (spec.startsWith('.')) target = resolve(dirname(file), spec);
    else continue; // dart: / package:flutter / package:dio 等外部依赖
    if (!existsSync(target)) {
      console.log(`IMPORT-MISS ${relative(root, file)} -> ${spec}`);
      errors++;
    }
  }
}

// --- 2) 括号 / 注释 / 字符串配平 ---
for (const file of files) {
  const src = readFileSync(file, 'utf8');
  let paren = 0, brace = 0, bracket = 0;
  let i = 0, state = 'code';
  while (i < src.length) {
    const c = src[i], n = src[i + 1];
    if (state === 'code') {
      if (c === '/' && n === '/') { state = 'line'; i += 2; continue; }
      if (c === '/' && n === '*') { state = 'block'; i += 2; continue; }
      if (c === "'") { state = 'single'; i++; continue; }
      if (c === '"') { state = 'double'; i++; continue; }
      if (c === '(') paren++;
      else if (c === ')') paren--;
      else if (c === '{') brace++;
      else if (c === '}') brace--;
      else if (c === '[') bracket++;
      else if (c === ']') bracket--;
      if (paren < 0 || brace < 0 || bracket < 0) break;
      i++;
      continue;
    }
    if (state === 'line') { if (c === '\n') state = 'code'; i++; continue; }
    if (state === 'block') { if (c === '*' && n === '/') { state = 'code'; i += 2; continue; } i++; continue; }
    if (state === 'single' || state === 'double') {
      const q = state === 'single' ? "'" : '"';
      if (c === '\\') { i += 2; continue; }
      if (c === '$' && n === '{') { // 字符串插值中的花括号需单独配平
        i += 2; let depth = 1;
        while (i < src.length && depth > 0) {
          if (src[i] === '{') depth++;
          else if (src[i] === '}') depth--;
          i++;
        }
        continue;
      }
      if (c === q) { state = 'code'; i++; continue; }
      i++; continue;
    }
  }
  if (paren !== 0 || brace !== 0 || bracket !== 0) {
    console.log(
      `UNBALANCED ${relative(root, file)} paren=${paren} brace=${brace} bracket=${bracket}`,
    );
    errors++;
  }
}

console.log(`\nchecked ${files.length} dart files, ${errors} problem(s)`);
process.exit(errors === 0 ? 0 : 1);
