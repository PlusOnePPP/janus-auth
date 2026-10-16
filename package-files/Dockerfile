# 离线单体部署：前端静态资源已打进 janus-backend 的 resources/static，
# 这里只负责把 maven 产出的 fat jar 跑在 JRE 上（基础镜像一次性联网拉取即可）。
FROM eclipse-temurin:17-jre-jammy

LABEL maintainer="janus" \
      description="Janus SSO 认证中心（Spring Boot 3.5 + JDK17 + 内置前端静态资源）"

WORKDIR /app

# 统一工作目录 janus_workspace：logs=日志，uploads=业务上传文件（头像等）
# 容器只挂这一个目录即可： -v $(pwd)/janus_workspace:/app/janus_workspace
VOLUME ["/app/janus_workspace"]

# 时区 / 中文 locale / JVM 内存（按需调整）
# JANUS_WORKSPACE_DIR：统一工作目录（配置 config/ + 日志 logs/ + 上传 uploads/ 都落在它下面），挂卷即持久化
# SPRING_CONFIG_ADDITIONAL_LOCATION：外挂配置目录（jar 内置配置之外的覆盖层）。
# 加 optional: 前缀：config/ 不存在时直接忽略，不报错（用户想覆盖配置时才建这个目录放 yml）
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    JANUS_WORKSPACE_DIR=/app/janus_workspace \
    SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:/app/janus_workspace/config/ \
    JAVA_OPTS="-Xms2g -Xmx4g"

# 复制 maven 打好的 fat jar 为固定名称 app.jar（避免 sources/javadoc 等多 jar 导致通配符匹配错乱）
COPY janus-backend/target/janus-backend-*.jar /app/app.jar

EXPOSE 8899

# 通过变量注入 JVM 参数；运行时用 -e DB_HOST/DB_USERNAME/DB_PASSWORD 连库
# 启动前先确保 janus_workspace 下的子目录存在（bind 挂载会覆盖镜像内预建目录，
# 故在容器启动时创建；因是双向挂卷，宿主机 ./janus_workspace/ 下也会同步生成）
ENTRYPOINT ["sh", "-c", "mkdir -p /app/janus_workspace/config /app/janus_workspace/logs /app/janus_workspace/uploads /app/janus_workspace/ssl && java $JAVA_OPTS -jar /app/app.jar"]
