# MARS-1 · API 用例 manifest + runner

> 由 T2 入库，**执行属 T3**（须等 G2.5 PASS 拿到部署地址）。

## 组成

| 文件 | 说明 |
| --- | --- |
| `manifest.json` | **148 条**接口用例（S3c 120 + T2 补充 28），契约基线 `api-contract v1.3` |
| `run_manifest.py` | 最小 runner（Python 3 标准库，零依赖）：按 `case_id` 选取，输出 `expected_http` / `expected_code` 断言 |

## 边界（重要）

- **默认离线**：`--list` 与默认模式不发任何网络请求。
- `--execute` 仅限 T3 / G2.5 PASS 之后，对**部署环境**执行；禁止对生产跑。
- 用例唯一来源是 `manifest.json`；runner 不改用例。

## 用法

```bash
# 列表（离线自查）
python tools/api/run_manifest.py --list

# 按 case_id 选取（可重复或逗号分隔）
python tools/api/run_manifest.py --case TC-I-ADM-001 --case TC-I-T2-ADM-028

# 按 API-ID / 优先级选取
python tools/api/run_manifest.py --api-id API-ADM-30
python tools/api/run_manifest.py --priority P1 --dry-run

# 真实执行（仅 T3）
python tools/api/run_manifest.py --execute \
  --base-url http://127.0.0.1:8080 --token "$MARS_TOKEN"
```

退出码：`0` 全通过 / `1` 有断言失败 / `2` 参数或数据错误。

## 已知限制

- 含路径参数占位符（`{}`）的用例在 `--execute` 下会 `SKIP`，需在 T3 补 `--path-param` 支持或先造数。
- 鉴权用例依赖真实 JWT；`--token` 或 `MARS_TOKEN` 注入，**不写入仓库**。
