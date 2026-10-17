# deploy · 本地一键演示（Docker Compose）

在单机上以 Docker 起「MySQL + Redis + 后端(local 档) + 运营后台」，用于**开发验证与演示**。
不新增业务功能，不改契约——只把仓库现有产物可运行化。

```
┌────────────┐   ┌────────────┐        ┌──────────────────────┐   ┌───────────────────┐
│  mysql:8   │   │  redis:7   │        │ backend (JRE17)      │   │ admin (nginx)     │
│  mars_live │◄──┤            │◄───────┤ SPRING_PROFILE=local │◄──┤ Vue3 构建产物     │
│  :3306     │   │  :6379     │        │ :8080  /api/v1/**    │   │ :80 → 宿主 :5174  │
└────────────┘   └────────────┘        └──────────────────────┘   └───────────────────┘
```

## 前置条件

- Docker Engine / Docker Desktop（含 `docker compose` v2+）。本编排在 `Docker Engine 29.5.2` + `Docker Compose v5.1.4` 上验证。
- 首次构建需联网拉取基础镜像（`mysql:8` / `redis:7` / `eclipse-temurin:17-*` / `node:20-alpine` / `nginx:1.27-alpine`）与 Maven / npm 依赖。
- 首次构建较慢：后端 `./gradlew clean check bootJar`（含单测）+ 前端 `npm ci && vite build`。

## 起 / 停 / 重置

```bash
cd deploy

# 1) 构建并后台启动全部服务
docker compose up -d --build

# 2) 查看状态（等 mysql / redis 变 healthy 后 backend 才会启动）
docker compose ps

# 3) 跟随日志（后端启动过程 / Flyway 迁移）
docker compose logs -f backend

# 停（保留数据）
docker compose down

# 重置（连同 MySQL / Redis 持久卷一并删除，下次 up 重新初始化并重放 Flyway）
docker compose down -v
```

> `down` 不会删除 `mars-mysql-data` / `mars-redis-data` 两个命名卷；`down -v` 才会。
> 重置后再次 `up -d`，Flyway 会在空库上重新应用 `V1`–`V3`。

## 访问地址

| 服务 | 地址 | 说明 |
| --- | --- | --- |
| 运营后台 | http://localhost:5174 | 默认走**前端内存 mock**（`VITE_USE_MOCK=true`），无后端也能看界面 |
| 后端 API | http://localhost:8080/api/v1/** | local 档 |
| MySQL | 127.0.0.1:3306 | 库名 `mars_live`，账号 `root` |
| Redis | 127.0.0.1:6379 | 无密码（本地演示） |

后台路由（HTML5 history）：`/moderation` 审核工作台 · `/finance` 财务对账与提现审核 · `/rooms` 直播间管理。
登录页为**角色入口**（`auditor` / `ops` / `finance`，一期为角色选择，无口令）。

## 环境变量

全部可选项；不设时用下列默认值。可在 `deploy/` 下建 `.env`（已被 `.gitignore` 忽略）或直接导出。

| 变量 | 默认值 | 作用 |
| --- | --- | --- |
| `MARS_DB_NAME` | `mars_live` | MySQL 库名（容器初始化即建库） |
| `MARS_DB_PASSWORD` | `mars_local_dev` | MySQL `root` 口令（**仅本地占位值**，真实环境必须覆盖） |
| `MARS_DB_HOST_PORT` | `3306` | MySQL 映射到宿主机的端口 |
| `MARS_REDIS_HOST_PORT` | `6379` | Redis 映射到宿主机的端口 |
| `MARS_HTTP_PORT` | `8080` | 后端映射到宿主机的端口 |
| `MARS_ADMIN_PORT` | `5174` | 后台映射到宿主机的端口 |
| `VITE_USE_MOCK` | `true` | 后台数据源：`true` 走内存 mock；`false` 走真实后端 |
| `VITE_API_BASE` | `/api/v1` | `VITE_USE_MOCK=false` 时前端请求基址（相对路径由 nginx 反代到 backend 容器） |

密钥一律不写进仓库：compose 用变量 + 本地占位默认值，后端 `MARS_JWT_SECRET` 等在 local 档下按 `application.yml` 的开发默认值运行。

## 端到端自检（无需后台界面）

local 档的固定开发验证码为 `000000`（`mars.auth.dev-code`）。

```bash
# 1) 发送验证码（占位实现：不真正发短信，只返回重发间隔）
curl -s -X POST http://localhost:8080/api/v1/auth/sms/send \
  -H 'Content-Type: application/json' \
  -d '{"phone":"13800000001","scene":"login"}'

# 2) 手机号 + 固定验证码登录 → 签发访问令牌
curl -s -X POST http://localhost:8080/api/v1/auth/login/sms \
  -H 'Content-Type: application/json' \
  -d '{"phone":"13800000001","code":"000000"}'
```

登录成功返回统一封装 `{"code":0,"data":{"accessToken":"...","expireIn":...}}`。

## 切到真实后端（关闭 mock）

```bash
VITE_USE_MOCK=false VITE_API_BASE=/api/v1 docker compose up -d --build admin
```

前端以 `/api/v1` 为基址请求 → `deploy/nginx.conf` 已把 `/api/` 反代到同编排的 `backend:8080`。

## 已知限制

- **仅本地/演示**：使用固定开发验证码 `000000`，MySQL/Redis 无强口令，`MARS_JWT_SECRET` 为开发默认值。**不得用于生产**。
- 后端以 `local` profile 运行，部分能力是占位实现（短信通道、充值档位、实名核验等），行为与未来真实实现不同。
- `db/migration` 以只读方式挂载到容器；**修改迁移脚本后需 `down -v` 重置数据卷**才能在新空库上重放（Flyway 不会对已应用的版本重新执行）。
- 后台默认 `VITE_USE_MOCK=true`，界面数据来自前端内存 mock，**不代表真实后端数据**；要验证前后端联通请按上文切到真实网关。
- 未暴露 `/actuator/health`（后端依赖中未引入 actuator）；启动就绪以日志 `Started MarsLiveApplication` 为准。
- 前端 App（`app/`，Flutter）非浏览器可演示物，不在本编排内。
