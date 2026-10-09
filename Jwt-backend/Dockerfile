# ============================================================
# 后端镜像：多阶段构建
#   阶段一用 Maven 镜像编译（镜像里有 JDK 和 Maven，几百 MB 但不进最终镜像）
#   阶段二只把打好的 jar 拷进精简的 JRE 镜像 —— 最终镜像小很多
# ============================================================

# ---------- 阶段一：构建 ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# 先只拷 pom 并下载依赖：这样只要 pom 没变，改 Java 代码时这一层能命中缓存，
# 不用每次重新下载几百 MB 依赖
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

# ---------- 阶段二：运行 ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 时区：容器默认 UTC，日志时间会对不上
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

COPY --from=build /app/target/*.jar app.jar

# 日志目录挂出来，方便宿主机查看
VOLUME ["/app/logs"]

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
