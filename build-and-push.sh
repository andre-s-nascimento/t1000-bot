#!/bin/bash
set -e

# ==============================
# 🔧 CONFIGURAÇÕES
# ==============================
APP_NAME="t1000-bot"
IMAGE_NAME="andresnascimento/t1000-bot"

# Cores
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

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
# 🏷️ VERSÃO DO build.gradle
# ==============================
extract_version() {
    if [ ! -f build.gradle ]; then
        log_error "build.gradle não encontrado!"
        exit 1
    fi
    VERSION=$(grep -E "^version = " build.gradle | head -1 | sed -E "s/version = ['\"]([^'\"]+)['\"].*/\1/")
    if [ -z "$VERSION" ]; then
        log_error "Não foi possível extrair a versão do build.gradle"
        exit 1
    fi
    log_info "Versão detectada: $VERSION"
}

# ==============================
# 🔐 LOGIN NO DOCKER HUB
# ==============================
docker_login() {
    if [ -z "$DOCKER_USERNAME" ] || [ -z "$DOCKER_TOKEN" ]; then
        log_error "DOCKER_USERNAME e/ou DOCKER_TOKEN não definidos no .env"
        exit 1
    fi
    log_info "Fazendo login no Docker Hub como $DOCKER_USERNAME..."
    echo "$DOCKER_TOKEN" | docker login -u "$DOCKER_USERNAME" --password-stdin
    log_info "✅ Login concluído."
}

# ==============================
# 🏗️ BUILD
# ==============================
build_image() {
    log_info "Buildando imagem: $IMAGE_NAME:$VERSION"
    docker build \
        -t "$IMAGE_NAME:latest" \
        -t "$IMAGE_NAME:$VERSION" \
        -t "$IMAGE_NAME:$(git rev-parse --short HEAD 2>/dev/null || echo 'nogit')" \
        .
    log_info "✅ Imagem buildada."
}

# ==============================
# 🚀 PUSH
# ==============================
push_image() {
    log_info "Enviando tags para o Docker Hub..."
    docker push "$IMAGE_NAME:latest"
    docker push "$IMAGE_NAME:$VERSION"
    SHORT_SHA=$(git rev-parse --short HEAD 2>/dev/null || echo "")
    if [ -n "$SHORT_SHA" ]; then
        docker push "$IMAGE_NAME:$SHORT_SHA"
    fi
    log_info "✅ Push concluído."
}

# ==============================
# 🚀 MAIN
# ==============================
main() {
    echo "========================================="
    echo "🏗️  Build & Push - $APP_NAME"
    echo "========================================="

    check_docker
    load_env
    extract_version

    case "${1:-build-push}" in
        build-push)
            log_info "Modo: build + push"
            docker_login
            build_image
            push_image
            log_info "🎉 Tudo pronto! Tags enviadas:"
            echo "   - $IMAGE_NAME:latest"
            echo "   - $IMAGE_NAME:$VERSION"
            echo "   - $IMAGE_NAME:$(git rev-parse --short HEAD 2>/dev/null || echo 'nogit')"
            ;;
        build)
            log_info "Modo: build apenas (sem push)"
            build_image
            log_info "✅ Imagem buildada localmente. Nada foi enviado."
            ;;
        *)
            echo "Uso: $0 {build-push|build}"
            exit 1
            ;;
    esac
}

main "$@"