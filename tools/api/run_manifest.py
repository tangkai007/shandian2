#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""MARS-1 API 用例 manifest runner（最小实现，零第三方依赖）。

设计边界（重要）：
  - 本工具**只做断言执行**，不生成/不修改用例；用例唯一来源 = manifest.json。
  - **T3 之前不得执行**：G2.5 PASS 后才允许对部署环境跑 `--execute`。
  - `--list` / 默认模式均为**纯离线**，不发任何网络请求。

用法：
  python tools/api/run_manifest.py --list
  python tools/api/run_manifest.py --case TC-I-ADM-001 --case TC-I-T2-ADM-030
  python tools/api/run_manifest.py --priority P1 --dry-run
  python tools/api/run_manifest.py --execute --base-url http://127.0.0.1:8080 --token <JWT>

退出码：
  0 = 全部断言通过（或 dry-run 正常完成）
  1 = 存在断言失败
  2 = 参数/数据错误
"""
import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
MANIFEST = os.path.join(HERE, "manifest.json")


def load_manifest(path=MANIFEST):
    if not os.path.exists(path):
        sys.stderr.write("manifest not found: %s\n" % path)
        sys.exit(2)
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def select(cases, ids, priorities, api_ids):
    out = cases
    if ids:
        wanted = {i.strip() for group in ids for i in group.split(",") if i.strip()}
        out = [c for c in out if c["case_id"] in wanted]
        missing = wanted - {c["case_id"] for c in out}
        if missing:
            sys.stderr.write("unknown case_id(s): %s\n" % ", ".join(sorted(missing)))
            sys.exit(2)
    if api_ids:
        wanted = {i.strip() for group in api_ids for i in group.split(",") if i.strip()}
        out = [c for c in out if c.get("api_id") in wanted]
    if priorities:
        wanted = {p.strip().upper() for group in priorities for p in group.split(",")}
        out = [c for c in out if c["priority"] in wanted]
    return out


def cmd_list(manifest, cases):
    print("MARS-1 API manifest — %s" % manifest.get("contract_baseline", ""))
    print("total=%d  selected=%d" % (manifest["summary"]["total"], len(cases)))
    print("%-22s %-6s %-6s %-5s %s" % ("CASE_ID", "METHOD", "HTTP", "CODE", "PATH"))
    print("-" * 110)
    for c in cases:
        print("%-22s %-6s %-6s %-5s %s" % (
            c["case_id"], c["method"], c["expected_http"], c["expected_code"], c["path"]))
    print("-" * 110)
    by_p = manifest["summary"].get("by_priority", {})
    print("by_priority: " + "  ".join("%s=%s" % (k, by_p.get(k, 0)) for k in ("P1", "P2", "P3")))


def cmd_dry(cases):
    print("DRY-RUN（不发起任何请求）")
    for c in cases:
        print("[%s] %s %s" % (c["case_id"], c["method"], c["path"]))
        print("    assert http == %s" % c["expected_http"])
        print("    assert body.code == %s" % c["expected_code"])
        for chk in c.get("checks", []):
            print("    check: %s" % chk)
    print("\nplanned assertions: %d case(s)" % len(cases))


def cmd_execute(cases, base_url, token, timeout):
    base = base_url.rstrip("/")
    failed = 0
    skipped = 0
    for c in cases:
        path = c["path"]
        if not c.get("runnable", True):
            print("SKIP  %s  (manifest 标记为不可直接执行)" % c["case_id"])
            skipped += 1
            continue
        if "{" in path:
            print("SKIP  %s  (path 含参数占位符，需 --path-param 支持)" % c["case_id"])
            skipped += 1
            continue
        url = base + path if path.startswith("/") else base + "/" + path
        req = urllib.request.Request(url, method=c["method"])
        if token:
            req.add_header("Authorization", "Bearer " + token)
        req.add_header("Accept", "application/json")
        try:
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                status, raw = resp.status, resp.read().decode("utf-8", "replace")
        except urllib.error.HTTPError as e:
            status, raw = e.code, e.read().decode("utf-8", "replace")
        except Exception as e:  # noqa: BLE001
            print("ERROR %s  %s" % (c["case_id"], e))
            failed += 1
            continue
        code = None
        try:
            code = json.loads(raw).get("code")
        except Exception:  # noqa: BLE001
            pass
        ok_http = status == c["expected_http"]
        ok_code = code == c["expected_code"]
        if ok_http and ok_code:
            print("PASS  %s  http=%s code=%s" % (c["case_id"], status, code))
        else:
            failed += 1
            print("FAIL  %s  http=%s(exp %s) code=%s(exp %s)" % (
                c["case_id"], status, c["expected_http"], code, c["expected_code"]))
    print("\nexecuted=%d failed=%d skipped=%d" % (len(cases) - skipped, failed, skipped))
    return 1 if failed else 0


def main(argv=None):
    ap = argparse.ArgumentParser(description="MARS-1 API manifest runner (offline by default)")
    ap.add_argument("--manifest", default=MANIFEST)
    ap.add_argument("--list", action="store_true", help="列出用例，不执行")
    ap.add_argument("--case", action="append", default=[], help="按 case_id 选取（可重复/逗号分隔）")
    ap.add_argument("--api-id", action="append", default=[], help="按 api_id 选取")
    ap.add_argument("--priority", action="append", default=[], help="按优先级选取 P1/P2/P3")
    ap.add_argument("--dry-run", action="store_true", help="打印断言计划，不发请求（默认行为）")
    ap.add_argument("--execute", action="store_true", help="真实执行（仅 T3 / G2.5 之后）")
    ap.add_argument("--base-url", default=os.environ.get("MARS_BASE_URL", ""))
    ap.add_argument("--token", default=os.environ.get("MARS_TOKEN", ""))
    ap.add_argument("--timeout", type=float, default=10.0)
    args = ap.parse_args(argv)

    manifest = load_manifest(args.manifest)
    cases = select(manifest["cases"], args.case, args.priority, args.api_id)
    if not cases:
        sys.stderr.write("no case selected\n")
        return 2

    if args.list:
        cmd_list(manifest, cases)
        return 0
    if args.execute:
        if not args.base_url:
            sys.stderr.write("--execute requires --base-url (or MARS_BASE_URL)\n")
            return 2
        return cmd_execute(cases, args.base_url, args.token, args.timeout)
    cmd_dry(cases)
    return 0


if __name__ == "__main__":
    sys.exit(main())
