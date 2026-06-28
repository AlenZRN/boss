# ---- 多阶段构建 (使用国内镜像 + 阿里云 Maven 源) ----
# Stage 1: 编译
FROM docker.m.daocloud.io/library/maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# 配置阿里云 Maven 镜像加速
RUN sed -i 's|<mirrors>|<mirrors><mirror><id>aliyun</id><name>aliyun</name><url>https://maven.aliyun.com/repository/public</url><mirrorOf>central</mirrorOf></mirror>|' /usr/share/maven/conf/settings.xml

COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# Stage 2: 运行
FROM docker.m.daocloud.io/library/eclipse-temurin:17-jre-alpine
WORKDIR /app

# 创建非 root 用户
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# 复制 JAR
COPY --from=builder /app/target/aimira-monitor-*.jar app.jar

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
