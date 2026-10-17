# 运营后台镜像：Node 构建 admin/（Vue 3 + Vite）→ Nginx 静态托管。
#
# 构建参数（compose 以 build.args 传入）：
#   VITE_USE_MOCK  默认 true —— 走前端内存 mock，无后端也能看界面；
#   VITE_API_BASE  默认 /api/v1 —— VITE_USE_MOCK=false 时前端请求基址，由 nginx.conf 反代到后端容器。

FROM node:20-alpine AS build

WORKDIR /app

# 先装依赖再拷源码，保证 package 未变时命中构建缓存。
COPY admin/package.json admin/package-lock.json ./
RUN npm ci

COPY admin/ ./

ARG VITE_USE_MOCK=true
ARG VITE_API_BASE=/api/v1
ENV VITE_USE_MOCK=${VITE_USE_MOCK} \
    VITE_API_BASE=${VITE_API_BASE}

# `build` = vue-tsc --noEmit && vite build → /app/dist
RUN npm run build

FROM nginx:1.27-alpine AS runtime

COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
