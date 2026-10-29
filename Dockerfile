# 运行镜像：只装 JRE，jar 在开发机用 mvn 打好后拷进来
# （不在服务器上跑 Maven，省内存省时间）
FROM eclipse-temurin:8-jre

WORKDIR /app
COPY target/repair-system-1.0.0.jar app.jar

# 注意：-XX:MaxRAMPercentage 在 JDK 8 属实验参数不可直接用，小内存机器改为固定 -Xmx
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
