#!/bin/bash
set -e

# ==============================
# 🔧 CONFIGURAÇÕES
# ==============================
APP_NAME="t1000-bot"
IMAGE_NAME="andresnascimento/t1000-bot"
IMAGE_TAG="latest"
DOCKER_IMAGE="$IMAGE_NAME:$IMAGE_TAG"

DATA_PATH="$(pwd)/temp_audio"

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# ==============================
# 📋 FUNÇÕES DE LOG COM TIMESTAMP
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
    if [ -f .env ]; then
        set -a
        source .env
        set +a
        log_info "Variáveis carregadas do .env"
    else
        log_error "Arquivo .env não encontrado!"
        exit 1
    fi
}

# ==============================
# 📦 PULL DA IMAGEM (OCI)
# ==============================
pull_image() {
    log_info "Baixando imagem do Docker Hub: $DOCKER_IMAGE"
    docker pull "$DOCKER_IMAGE" || {
        log_error "Falha ao baixar imagem. Verifique sua conexão e login."
        exit 1
    }
    log_info "✅ Imagem baixada com sucesso."
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
# 🚀 RUN
# ==============================
run_container() {
    log_info "Iniciando container do $APP_NAME"

    mkdir -p "$DATA_PATH"
    mkdir -p "$(pwd)/logs"
    chmod 777 "$(pwd)/logs"
    # Garante permissões corretas para o banco
    sudo chown -R 1000:1000 data 2>/dev/null || true
    sudo chmod 666 data/t1000.db 2>/dev/null || true

    docker run -d \
        --name "$APP_NAME" \
        --restart unless-stopped \
        --env-file .env \
        -p 8082:8082 \
        -e TZ=America/Sao_Paulo \
        -v "$DATA_PATH:/app/temp_audio" \
        -v "$(pwd)/data:/app/data" \
        -v "$(pwd)/logs:/app/logs" \
        -v "$(pwd)/config/easter-eggs.json:/app/config/easter-eggs.json" \
        -v "$(pwd)/config/auto-responses.json:/app/config/auto-responses.json" \
        -v "$(pwd)/media:/app/media" \
        --memory="700m" \
        --memory-reservation="512m" \
        --cpus="0.8" \
        "$DOCKER_IMAGE" || {
            log_error "Erro ao iniciar container"
            exit 1
        }

    log_info "✅ Container rodando!"
}

# ==============================
# 📊 STATUS & LOGS
# ==============================
show_status() {
    docker ps --filter "name=$APP_NAME"
}

show_logs() {
    docker logs --tail 50 "$APP_NAME"
}

logs_follow() {
    docker logs -f "$APP_NAME"
}

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
            pull_image
            stop_container
            run_container
            cleanup_docker
            show_status
            show_logs
            ;;
        restart)
            log_info "Modo: restart apenas"
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