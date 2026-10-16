# 后端镜像（S1a v0.4 §5.4 `deploy/`）
#
# 构建阶段：Gradle 8 + JDK 17 → 编译 + 校验（JUnit + 零依赖 spec 套件）+ 打出可执行 bootJar
# 运行阶段：JRE 17 直接运行 `app.jar`（Flyway 迁移在启动时按 db/migration 执行）

FROM gradle:8.10-jdk17 AS build

WORKDIR /workspace
COPY backend /workspace/backend
WORKDIR /workspace/backend

# `check` = compile + JUnit 5（装配层切片）+ specCheck（领域核心：AC-1/4/5/6/8）
# `bootJar` = 可执行 fat jar
RUN gradle --no-daemon --console=plain clean check bootJar

FROM eclipse-temurin:17-jre AS runtime

# Flyway 迁移脚本随镜像分发（仓库根 db/migration，路径以环境变量覆盖）
WORKDIR /workspace
COPY db /workspace/db

WORKDIR /app
COPY --from=build /workspace/backend/build/libs/*.jar /app/app.jar

ENV MARS_DB_MIGRATION_DIR=/workspace/db/migration \
    MARS_SERVER_PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

# 生产启动必需环境变量（缺失即启动失败，见 application-prod.yml）：
#   MARS_JWT_SECRET / MARS_DB_HOST / MARS_DB_PASSWORD / MARS_REDIS_HOST / MARS_REDIS_PASSWORD
# 且须提供真实实现替换占位 bean：DevSmsCodeVerifier / DefaultRechargePackageCatalog / DefaultViewerRiskProfile
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
