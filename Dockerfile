# ============================================================================
# Stage 1: Build
# ============================================================================
FROM amazoncorretto:21-alpine3.20 AS build

WORKDIR /app

# Instala bash (necessário para alguns scripts do Gradle)
RUN apk add --no-cache bash

# Copia o wrapper do Gradle e arquivos de build primeiro (melhor cache)
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

RUN chmod +x gradlew

# Baixa dependências (cacheável)
RUN ./gradlew dependencies --no-daemon || true

# Copia o código-fonte
COPY src src

# Copia os recursos externos necessários em runtime.
# ⚠️ Serve como FALLBACK — em prod, /app/config deve ser montado como volume.
COPY config config

# Build final (sem testes, com clean)
RUN ./gradlew clean bootJar -x test --no-daemon

# ============================================================================
# Stage 2: Runtime
# ============================================================================
FROM amazoncorretto:21-alpine3.20

WORKDIR /app

# Instala ffmpeg, certificados e timezone data
RUN apk add --no-cache ffmpeg ca-certificates tzdata

# Configura timezone
ENV TZ=America/Sao_Paulo
RUN cp /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# Cria usuário não-root
RUN addgroup -g 1000 appgroup && \
    adduser -u 1000 -G appgroup -S -D appuser

# Cria diretórios de trabalho (incluindo /app/certs vazio).
# ⚠️ /app/certs é preenchido em runtime pelo deploy-oci.sh (volume).
RUN mkdir -p /app/temp /app/config /app/certs /app/logs && \
    chown -R appuser:appgroup /app

# Copia o JAR da aplicação
COPY --from=build /app/build/libs/*.jar /app/app.jar

# ❌ REMOVIDO: COPY --chown=appuser:appgroup certs/aiven-truststore.jks /app/certs/
# Motivo: o .jks é sensível e não vai pro Git. O deploy-oci.sh monta via volume.
# Se o truststore NÃO for montado em runtime, o Kafka falha ao subir.

# Copia os arquivos de configuração externos como FALLBACK.
# Se /app/config for montado como volume, o conteúdo do volume tem precedência.
COPY --chown=appuser:appgroup config /app/config

# Troca para usuário não-root
USER appuser

# Diretório temporário do app
ENV APP_TEMP_DIR=/app/temp

# Path do truststore (usado no application.properties)
ENV KAFKA_TRUSTSTORE_PATH=/app/certs/aiven-truststore.jks

# Path do arquivo de persistência das feature flags
ENV FEATURE_FLAGS_PERSIST_PATH=/app/config/feature-flags.json
ENV FEATURE_FLAGS_PERSIST_ENABLED=true

# JVM otimizada para containers pequenos
ENV JAVA_OPTS="-XX:+UseSerialGC \
    -XX:MaxRAMPercentage=75 \
    -XX:InitialRAMPercentage=50 \
    -XX:+ExitOnOutOfMemoryError \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/app/temp \
    -Djava.security.egd=file:/dev/./urandom \
    -Duser.timezone=America/Sao_Paulo \
    -Dfile.encoding=UTF-8"

# Documenta os volumes esperados
VOLUME ["/app/temp", "/app/logs", "/app/config", "/app/certs"]

EXPOSE 8082

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]