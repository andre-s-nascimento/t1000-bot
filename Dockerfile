# Stage 1: Build
FROM amazoncorretto:21-alpine3.20 AS build
WORKDIR /app

# Cache do Gradle (ESSENCIAL)
ENV GRADLE_USER_HOME=/gradle-cache

# Copia apenas o necessário primeiro (melhor cache)
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

# Baixa dependências (cacheável)
# RUN ./gradlew build -x test --parallel --build-cache || true

# Agora copia o código
COPY src src

# Build final (rápido porque já cacheou)
RUN ./gradlew bootJar -x test --no-daemon

# Stage 2: Runtime (menor possível)
FROM amazoncorretto:21-alpine3.20
WORKDIR /app

# Instalar ffmpeg e certificados (Alpine usa apk)
RUN apk add --no-cache ffmpeg ca-certificates

COPY --from=build /app/build/libs/*.jar app.jar

# Usuário não-root (segurança)
# Usuário não-root com UID 1000
RUN addgroup -g 1000 appgroup && adduser -u 1000 -G appgroup -S appuser
RUN mkdir -p /app/temp && chown -R appuser /app/temp
USER appuser

# Porta (opcional, se seu bot expor HTTP)
# EXPOSE 8080

# Comando de entrada (otimizado para baixa memória)
ENTRYPOINT ["java", "-XX:+UseSerialGC", "-Xms128m", "-Xmx256m", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
