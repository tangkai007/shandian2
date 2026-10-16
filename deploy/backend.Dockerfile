# 后端构建/校验镜像（S1a v0.4 §5.4 `deploy/`）
#
# 本增量（P0 脚手架 + 核心域逻辑）的镜像职责 = **构建 + 跑通校验**：
#   - Gradle 8 + JDK 17 编译 `backend/`
#   - `gradle check` 触发 JUnit（暂无）+ `specCheck`（零依赖 spec 套件，AC-4 / AC-8 核心断言）
#
# 运行态（Web/持久化层）随下一次增量接入，届时启用文件末尾的 runtime 阶段。

FROM gradle:8.10-jdk17 AS build

WORKDIR /workspace
COPY backend /workspace/backend
WORKDIR /workspace/backend

# `check` = 编译 + `test`（JUnit 5）+ `specCheck`（零依赖 spec 套件）
RUN gradle --no-daemon --console=plain clean check

# ---------------------------------------------------------------------------
# 运行态（下一次增量启用）：Web/持久化层落地后，`bootJar` 产出可执行包，
# 再打开下面的 runtime 阶段并把镜像入口指向它。
#
# FROM eclipse-temurin:17-jre AS runtime
# WORKDIR /app
# COPY --from=build /workspace/backend/build/libs/*.jar /app/app.jar
# EXPOSE 8080
# ENTRYPOINT ["java", "-jar", "/app/app.jar"]
# ---------------------------------------------------------------------------
