FROM eclipse-temurin:17-jdk-jammy

# 设置工作目录
WORKDIR /app

# 设置环境变量
ENV JAVA_OPTS="-Xmx256m -XX:+UseZGC"
ENV TZ=Asia/Shanghai

# 暴露应用程序端口
EXPOSE 8080 8443

# 创建非root用户
RUN groupadd -r springuser && useradd -r -g springuser springuser

# 复制JAR文件
COPY target/flink-sensor-simulator-1.0-SNAPSHOT.jar /app/app.jar

# 创建日志目录并设置权限
RUN mkdir -p /app/logs && \
    chown -R springuser:springuser /app

# 切换到非root用户
USER springuser

# 添加健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
  CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

# 启动应用程序
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"] 