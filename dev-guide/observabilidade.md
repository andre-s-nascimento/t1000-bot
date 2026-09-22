# 📋 Resumo do que fizemos + Template de PR

Vou dividir em 3 partes: **(1) resumo executivo**, **(2) guia passo a passo para reproduzir**, e **(3) template de PR pronto para colar no GitHub**.

---

## Parte 1 — Resumo executivo

Nesta sessão, evoluímos a **Sprint 3 (Observabilidade Avançada)** do projeto T1000 Bot com dois pilares:

### 🎯 Objetivo

Eliminar a dependência de logs em console e métricas em memória, implementando **monitoramento proativo** e **centralização de logs**.

### ✅ O que foi entregue

**1. Métricas com Micrometer + Prometheus + Grafana**

- Dependência `micrometer-registry-prometheus` adicionada
- `MetricsService` refatorado com contadores `t1000.operations.total{operation, status}`
- Endpoint `/actuator/prometheus` exposto
- Prometheus configurado para scrapar `host.docker.internal:8082`
- Grafana provisionado para consumir o Prometheus

**2. Logs estruturados com Logback + Logstash + Elasticsearch + Kibana (ELK Stack)**

- Dependência `logstash-logback-encoder:9.0` adicionada
- `logback-spring.xml` criado com **dois appenders**: `CONSOLE` (humano) e `JSON_FILE` (estruturado)
- Arquivo `logs/t1000-bot.json` gerado com **uma linha JSON por log**
- Serviço **Logstash** adicionado ao `docker-compose.yml` para fazer o _shipping_ do arquivo JSON para o ES
- Pipeline `logstash.conf` configurado para ler, parsear e indexar em `t1000-logs-YYYY.MM.dd`
- **Data View** criado no Kibana para exploração via KQL e dashboards

### 🧠 Decisões arquiteturais relevantes

| Decisão                                                      | Por quê                                                                |
| ------------------------------------------------------------ | ---------------------------------------------------------------------- |
| **Logstash como intermediário** (vs. appender direto pro ES) | Desacoplamento, resiliência, buffer, sem impacto na performance da app |
| **JSON por linha** (não multiline)                           | Pré-requisito para indexação eficiente no ES                           |
| **Índice diário** (`t1000-logs-%{+YYYY.MM.dd}`)              | Facilita retention policy e limpeza                                    |
| **`@timestamp` explícito no encoder**                        | Kibana exige esse campo como time field                                |
| **Dois appenders (console + JSON)**                          | Dev vê no terminal; produção vê no Kibana                              |

### 🐛 Problema que resolvemos

Logs apareciam no console e no arquivo JSON, mas **não chegavam ao Kibana** porque faltava o agente de shipping (Logstash). Adicionamos o serviço e configuramos o pipeline de ingestão.

---

## Parte 2 — Guia passo a passo (para reprodutores)

### 🧩 Pré-requisitos

- Docker e Docker Compose instalados
- Aplicação Spring Boot rodando localmente na porta `8082`
- Java 21

### 📦 Passo 1 — Adicionar dependências no `build.gradle`

```gradle
dependencies {
    // Observabilidade
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'io.micrometer:micrometer-registry-prometheus'
    implementation 'net.logstash.logback:logstash-logback-encoder:9.0'
}
```

E habilitar o endpoint no `application.properties`:

```properties
management.endpoints.web.exposure.include=health,info,prometheus
management.endpoint.prometheus.access=unrestricted
```

### 📦 Passo 2 — Configurar `logback-spring.xml`

Criar em `src/main/resources/logback-spring.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

    <!-- Appender humano (console) -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>${CONSOLE_LOG_PATTERN}</pattern>
        </encoder>
    </appender>

    <!-- Appender estruturado (JSON) -->
    <appender name="JSON_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/t1000-bot.json</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/t1000-bot-%d{yyyy-MM-dd}.json.gz</fileNamePattern>
            <maxHistory>7</maxHistory>
        </rollingPolicy>
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <timestampPattern>yyyy-MM-dd'T'HH:mm:ss.SSSXXX</timestampPattern>
            <timeZone>America/Sao_Paulo</timeZone>
            <customFields>{"application":"t1000-bot","env":"local"}</customFields>
            <throwableConverter class="net.logstash.logback.stacktrace.ShortenedThrowableConverter">
                <maxDepthPerThrowable>20</maxDepthPerThrowable>
                <maxLength>4096</maxLength>
                <rootCauseFirst>true</rootCauseFirst>
            </throwableConverter>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="JSON_FILE"/>
    </root>
</configuration>
```

### 📦 Passo 3 — `docker-compose.yml` atualizado

```yaml
services:
  # ========== MÉTRICAS ==========
  prometheus:
    image: prom/prometheus:latest
    container_name: t1000-prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
    extra_hosts:
      - "host.docker.internal:host-gateway"

  grafana:
    image: grafana/grafana:latest
    container_name: t1000-grafana
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_USER=admin
      - GF_SECURITY_ADMIN_PASSWORD=admin
    depends_on:
      - prometheus

  # ========== LOGS ==========
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.11.3
    container_name: t1000-elasticsearch
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
    ports:
      - "9200:9200"
    volumes:
      - es-data:/usr/share/elasticsearch/data

  kibana:
    image: docker.elastic.co/kibana/kibana:8.11.3
    container_name: t1000-kibana
    ports:
      - "5601:5601"
    environment:
      - ELASTICSEARCH_HOSTS=http://elasticsearch:9200
    depends_on:
      - elasticsearch

  logstash:
    image: docker.elastic.co/logstash/logstash:8.11.3
    container_name: t1000-logstash
    volumes:
      - ./logstash.conf:/usr/share/logstash/pipeline/logstash.conf:ro
      - ./logs:/usr/share/logstash/logs:ro
    depends_on:
      - elasticsearch

volumes:
  es-data:
```

### 📦 Passo 4 — `prometheus.yml`

```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: "t1000-bot"
    metrics_path: "/actuator/prometheus"
    static_configs:
      - targets: ["host.docker.internal:8082"]
```

### 📦 Passo 5 — `logstash.conf`

```conf
input {
  file {
    path => "/usr/share/logstash/logs/t1000-bot.json"
    start_position => "beginning"
    sincedb_path => "/dev/null"
    codec => "json"
  }
}

filter {
  # Nada a fazer: o log já vem em JSON estruturado
}

output {
  elasticsearch {
    hosts => ["http://elasticsearch:9200"]
    index => "t1000-logs-%{+YYYY.MM.dd}"
  }
  stdout { codec => rubydebug }
}
```

### 📦 Passo 6 — Subir tudo

```bash
# Build e start
docker-compose up -d

# Confirmar containers
docker-compose ps

# Confirmar logs do Logstash lendo o arquivo
docker logs -f t1000-logstash
```

### 📦 Passo 7 — Verificar ingestão no ES

```bash
# Índices criados
curl -s "localhost:9200/_cat/indices?v" | grep t1000

# Contagem de docs
curl -s "localhost:9200/t1000-logs-*/_count?pretty"

# Últimos logs
curl -s "localhost:9200/t1000-logs-*/_search?pretty&size=5&sort=@timestamp:desc"
```

### 📦 Passo 8 — Configurar Kibana

1. Acesse `http://localhost:5601`
2. **Stack Management** → **Data Views** → **Create data view**
   - Name: `T1000 Logs`
   - Index pattern: `t1000-logs-*`
   - Timestamp field: `@timestamp`
3. **Analytics** → **Discover** → selecione o Data View
4. Adicione colunas: `@timestamp`, `level`, `thread_name`, `logger_name`, `message`
5. Filtre com KQL:
   ```
   level: "ERROR"
   logger_name: "*AudioWorkerService*"
   message: "FFmpeg"
   ```

### 📦 Passo 9 — Configurar Grafana

1. Acesse `http://localhost:3000` (admin/admin)
2. **Configuration** → **Data Sources** → **Add Prometheus**
   - URL: `http://prometheus:9090`
3. **Create** → **Dashboard** → **Add visualization**
4. Query PromQL:
   ```promql
   rate(t1000_operations_total{status="error"}[5m])
   sum by (operation) (t1000_operations_total)
   ```

---

## Parte 3 — Template do Pull Request

> Copie o conteúdo abaixo e cole na descrição do PR no GitHub.

---

### Título do PR

```
feat(observability): Sprint 3 — Micrometer/Prometheus/Grafana + ELK Stack para logs centralizados
```

### 🧾 Descrição

Este PR implementa a **Sprint 3 — Observabilidade Avançada**, entregando:

1. **Métricas de aplicação** com Micrometer, expostas via `/actuator/prometheus` e visualizadas no Grafana.
2. **Logs centralizados** com a stack ELK (Elasticsearch + Logstash + Kibana), consumindo o arquivo JSON gerado pelo Logback.

O objetivo é eliminar a dependência de logs em console e métricas em memória, viabilizando **monitoramento proativo**, **busca estruturada** e **dashboards acionáveis**.

---

### 🎯 Motivação

- Logs em console são **efêmeros** e não permitem consultas históricas.
- Diagnóstico de incidentes exigia `ssh` + `grep`, o que é inviável em produção.
- Métricas em memória não sobrevivem a restart da aplicação.
- Precisamos de uma base sólida de observabilidade antes de escalar para múltiplas instâncias.

---

### 🏗️ Arquitetura da Solução

```
┌─────────────────────┐
│   T1000 Bot (Java)  │
│                     │
│  Logback            │─────► logs/t1000-bot.json (JSON por linha)
│  Micrometer         │─────► /actuator/prometheus
└─────────┬───────────┘
          │
          │ (volume mapeado)
          ▼
┌─────────────────────┐        ┌──────────────────┐
│     Logstash        │───────►│  Elasticsearch   │
│  (shipping/parse)   │        │  t1000-logs-*    │
└─────────────────────┘        └────────┬─────────┘
                                        │
                                        ▼
                               ┌──────────────────┐
                               │     Kibana       │
                               │  Discover / KQL  │
                               └──────────────────┘

┌─────────────────────┐        ┌──────────────────┐
│    Prometheus       │◄───────│ /actuator/       │
│  (scrape 15s)       │        │  prometheus      │
└─────────┬───────────┘        └──────────────────┘
          │
          ▼
┌─────────────────────┐
│      Grafana        │
│  (PromQL queries)   │
└─────────────────────┘
```

---

### 📦 Mudanças incluídas

#### Dependências (`build.gradle`)

```gradle
implementation 'org.springframework.boot:spring-boot-starter-actuator'
implementation 'io.micrometer:micrometer-registry-prometheus'
implementation 'net.logstash.logback:logstash-logback-encoder:9.0'
```

#### Configuração da aplicação (`application.properties`)

```properties
management.endpoints.web.exposure.include=health,info,prometheus
management.endpoint.prometheus.access=unrestricted
```

#### Novos arquivos

| Arquivo                                 | Descrição                                               |
| --------------------------------------- | ------------------------------------------------------- |
| `src/main/resources/logback-spring.xml` | Appenders `CONSOLE` e `JSON_FILE` com `LogstashEncoder` |
| `logstash.conf`                         | Pipeline de ingestão do arquivo JSON para o ES          |
| `prometheus.yml`                        | Config de scrape do endpoint `/actuator/prometheus`     |

#### Arquivos modificados

| Arquivo                  | Mudança                                                                 |
| ------------------------ | ----------------------------------------------------------------------- |
| `build.gradle`           | Dependências de observabilidade                                         |
| `docker-compose.yml`     | Serviços `elasticsearch`, `kibana`, `logstash`, `prometheus`, `grafana` |
| `application.properties` | Exposição do Actuator/Prometheus                                        |

---

### 🧪 Como testar

#### 1. Subir a stack de observabilidade

```bash
docker-compose up -d
docker-compose ps
```

Esperado: 5 containers `Up` (`t1000-elasticsearch`, `t1000-kibana`, `t1000-logstash`, `t1000-prometheus`, `t1000-grafana`).

#### 2. Subir a aplicação

```bash
./gradlew bootRun
```

#### 3. Gerar logs

Envie uma mensagem de áudio para o bot ou chame qualquer endpoint que dispare processamento (`/admin/test-podcast`, `/admin/test-morning-digest`, etc.).

#### 4. Validar métricas

```bash
curl -s http://localhost:8082/actuator/prometheus | grep t1000_operations
```

Esperado: contadores no formato `t1000_operations_total{operation="...", status="success|error"}`.

#### 5. Validar logs no Elasticsearch

```bash
curl -s "localhost:9200/_cat/indices?v" | grep t1000
curl -s "localhost:9200/t1000-logs-*/_count?pretty"
```

Esperado: índice `t1000-logs-YYYY.MM.dd` com `docs.count > 0`.

#### 6. Visualizar no Kibana

1. `http://localhost:5601`
2. Criar Data View `t1000-logs-*` com `@timestamp` como time field
3. Discover → filtrar `logger_name: "*AudioWorkerService*"`

Esperado: logs estruturados com colunas `@timestamp`, `level`, `thread_name`, `logger_name`, `message`.

#### 7. Visualizar no Grafana

1. `http://localhost:3000` (admin/admin)
2. Data Source Prometheus: `http://prometheus:9090`
3. Query: `rate(t1000_operations_total[5m])`

Esperado: gráfico com a taxa de operações por segundo.

---

### 🐛 Troubleshooting conhecido

| Sintoma                   | Causa                              | Solução                                                 |
| ------------------------- | ---------------------------------- | ------------------------------------------------------- |
| Kibana sem dados          | Data View não criado               | Criar `t1000-logs-*` com `@timestamp`                   |
| `@timestamp` ausente      | Encoder sem `timestampPattern`     | Configurar `<timestampPattern>` no `logback-spring.xml` |
| Logstash em restart loop  | Erro no `logstash.conf`            | `docker logs t1000-logstash`                            |
| Logs antigos não aparecem | `sincedb` memorizou offset         | Usar `sincedb_path => "/dev/null"` (dev)                |
| Prometheus sem targets    | `host.docker.internal` não resolve | Adicionar `extra_hosts` no compose (Linux)              |
| Métricas vazias           | Endpoint não exposto               | Verificar `management.endpoints.web.exposure.include`   |

---

### ✅ Checklist

- [x] Dependências adicionadas ao `build.gradle`
- [x] `logback-spring.xml` com appenders `CONSOLE` e `JSON_FILE`
- [x] Arquivo `logs/t1000-bot.json` gerado com JSON por linha
- [x] Serviço Logstash configurado para shipping
- [x] Índice `t1000-logs-*` criado no Elasticsearch
- [x] Data View configurado no Kibana
- [x] Endpoint `/actuator/prometheus` exposto
- [x] Prometheus configurado para scrape
- [x] Grafana conectado ao Prometheus
- [x] Documentação de troubleshooting adicionada

---

### 🔗 Referências

- [Micrometer Prometheus Registry](https://micrometer.io/docs/registry/prometheus)
- [Logstash Logback Encoder](https://github.com/logstash/logstash-logback-encoder)
- [Elastic Stack 8.x Docker](https://www.elastic.co/guide/en/elastic-stack-get-started/current/get-started-docker.html)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)

---

### 🚀 Próximos passos (fora do escopo deste PR)

- [ ] Configurar **Index Lifecycle Management (ILM)** no ES para retention automática
- [ ] Adicionar **alertas** no Kibana (ex.: `level: ERROR` > 10/min)
- [ ] Criar dashboards pré-provisionados (JSON) versionados no repo
- [ ] Adicionar `traceId`/`spanId` para correlação entre logs (Micrometer Tracing)
- [ ] Configurar **Filebeat** em produção (mais leve que Logstash para shipping)

---

### 🧑‍💻 Autor

Sprint 3 — Observabilidade Avançada
Co-authored-by: T-1000 Bot <bot@t1000.local>

---

Pronto! É só copiar esse conteúdo para a descrição do PR. Se quiser, posso também:

- Gerar o **JSON do dashboard do Grafana** para versionar no repo
- Gerar o **JSON do dashboard do Kibana** para importar direto
- Criar um **`docker-compose.observability.yml`** separado para não misturar com o compose principal

É só pedir! 🚀
