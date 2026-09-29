#!/usr/bin/env bash
set -euo pipefail

# ==============================
# 🔧 CONFIGURAÇÕES
# ==============================
APP_NAME="t1000-bot"
IMAGE_NAME="andresnascimento/t1000-bot"
IMAGE_TAG="latest"
DOCKER_IMAGE="$IMAGE_NAME:$IMAGE_TAG"

TEMP_PATH="$(pwd)/temp_audio"
ENV_FILE=""

# Cores
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# ==============================
# 📋 LOG
# ==============================
timestamp() { date +"%Y-%m-%d %H:%M:%S"; }
log_info()  { echo -e "$(timestamp) ${GREEN}[INFO]${NC} $1"; }
log_warn()  { echo -e "$(timestamp) ${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "$(timestamp) ${RED}[ERROR]${NC} $1"; }

# ==============================
# 🔍 VALIDAÇÕES
# ==============================
check_docker() {
    if ! command -v docker &>/dev/null; then
        log_error "Docker não está instalado ou não está no PATH"
        exit 1
    fi
}

load_env() {
    if [ -f .env.prod ]; then
        ENV_FILE=".env.prod"
    elif [ -f .env ]; then
        ENV_FILE=".env"
        log_warn "Usando .env (dev) — ideal é .env.prod em produção"
    else
        log_error "Nenhum arquivo .env ou .env.prod encontrado!"
        exit 1
    fi

    set -a
    source "$ENV_FILE"
    set +a
    log_info "Variáveis carregadas de $ENV_FILE"
}

# ==============================
# 📦 PULL
# ==============================
pull_image() {
    log_info "Baixando imagem do Docker Hub: $DOCKER_IMAGE"
    docker pull "$DOCKER_IMAGE" || {
        log_error "Falha ao baixar imagem. Verifique sua conexão e login."
        exit 1
    }
    log_info "✅ Imagem baixada."
}

# ==============================
# 💾 BACKUP
# ==============================
backup_database() {
    log_info "Iniciando backup do banco de dados..."
    if [ -f "data/t1000.db" ]; then
        BACKUP_FILE="data/t1000_backup_$(date +%Y%m%d_%H%M%S).db"
        cp data/t1000.db "$BACKUP_FILE"
        log_info "✅ Backup concluído: $BACKUP_FILE"
    else
        log_warn "Banco de dados não encontrado em data/t1000.db. Pulando backup."
    fi
}

# ==============================
# 🧹 LIMPEZA
# ==============================
stop_container() {
    log_info "🛑 Parando container antigo (se existir)..."
    docker stop "$APP_NAME" &>/dev/null || true
    docker rm "$APP_NAME" &>/dev/null || true
}

cleanup_docker() {
    log_info "🧹 Removendo imagens não utilizadas (opcional)..."
    docker image prune -f &>/dev/null || true
}

# ==============================
# ⚙️ CONFIG FILES
# ==============================
ensure_config_files() {
    local config_dir="$(pwd)/config"
    mkdir -p "$config_dir"

    local required_files=(
        "easter-eggs.json"
        "auto-responses.json"
        "worldcup2026.json"
    )

    for file in "${required_files[@]}"; do
        if [ ! -f "$config_dir/$file" ]; then
            log_warn "Arquivo $file não existe — criando vazio (verifique o conteúdo!)"
            echo '{}' > "$config_dir/$file"
        fi
    done

    # A aplicação cria/atualiza, mas precisa existir pra não virar diretório
    if [ ! -f "$config_dir/feature-flags.json" ]; then
        log_info "Inicializando feature-flags.json (persistência de flags)"
        echo '{}' > "$config_dir/feature-flags.json"
    fi
}

# ==============================
# 🚀 RUN
# ==============================
run_container() {
    log_info "Iniciando container do $APP_NAME (env: $ENV_FILE)"

    mkdir -p "$TEMP_PATH"
    mkdir -p "$(pwd)/logs"
    mkdir -p "$(pwd)/data"
    mkdir -p "$(pwd)/media"
    mkdir -p "$(pwd)/config"

    # Antes do docker run, verifica se o truststore existe
if [ ! -f "$(pwd)/certs/aiven-truststore.jks" ]; then
    log_error "❌ certs/aiven-truststore.jks não encontrado no host!"
    log_error "   Baixe o truststore do Aiven e coloque em $(pwd)/certs/"
    exit 1
fi

    # 🔥 NOVO — garantir que os arquivos de config existem no host
    # Sem isso, um primeiro deploy deixaria /app/config vazio dentro do container
    ensure_config_files

    sudo chown -R "$(id -u):$(id -g)" \
    "$TEMP_PATH" \
    "$(pwd)/logs" \
    "$(pwd)/data" \
    "$(pwd)/media" \
    "$(pwd)/config" \
    2>/dev/null || true

    # 🔧 FIX CRÍTICO: --env-file "$ENV_FILE"
    docker run -d \
    --name "$APP_NAME" \
    --restart unless-stopped \
    --user "$(id -u):$(id -g)" \
    --env-file "$ENV_FILE" \
    -p 8082:8082 \
    -e TZ=America/Sao_Paulo \
    -v "$TEMP_PATH:/app/temp" \
    -v "$(pwd)/data:/app/data" \
    -v "$(pwd)/logs:/app/logs" \
    -v "$(pwd)/config:/app/config" \
    -v "$(pwd)/certs:/app/certs" \
    -v "$(pwd)/media:/app/media" \
    --memory="700m" \
    --memory-reservation="512m" \
    --cpus="0.8" \
    "$DOCKER_IMAGE" || {
            log_error "Erro ao iniciar container"
            exit 1
        }

    log_info "✅ Container rodando!"
    log_info "   feature-flags.json persistido em $(pwd)/config/feature-flags.json"
}

# ==============================
# 📊 STATUS & LOGS
# ==============================
show_status() { docker ps --filter "name=$APP_NAME"; }
show_logs()   { docker logs --tail 50 "$APP_NAME"; }
logs_follow() { docker logs -f "$APP_NAME"; }

# ==============================
# 🚀 MAIN
# ==============================
main() {
    echo "========================================="
    echo "☁️ Deploy OCI - $APP_NAME (pull da imagem)"
    echo "========================================="

    check_docker
    load_env

    case "${1:-deploy}" in
        deploy)
            log_info "Modo: deploy OCI completo (pull + restart)"
            backup_database
            pull_image
            stop_container
            run_container
            cleanup_docker
            show_status
            show_logs
            ;;
        restart)
            log_info "Modo: restart apenas"
            backup_database
            pull_image
            stop_container
            run_container
            show_status
            ;;
        stop)
            stop_container
            log_info "Container parado"
            ;;
        logs)
            logs_follow
            ;;
        status)
            show_status
            ;;
        pull)
            pull_image
            ;;
        *)
            echo "Uso: $0 {deploy|restart|stop|logs|status|pull}"
            exit 1
            ;;
    esac
}

main "$@"